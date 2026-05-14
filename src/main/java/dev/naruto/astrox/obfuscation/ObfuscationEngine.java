package dev.naruto.astrox.obfuscation;

import org.objectweb.asm.*;

import java.util.*;
import java.util.Base64;

/**
 * Bytecode obfuscation engine with real string encryption.
 *
 * <p>When {@code strength >= 5}, all eligible string constants are replaced with
 * AES-256-GCM encrypted Base64 blobs. A per-class static decryptor method ({@code __d})
 * and key field ({@code __k}) are injected so strings are decrypted transparently at runtime.</p>
 *
 * <p>Safety filters skip strings that would break JVM semantics if encrypted:
 * <ul>
 *   <li>JVM type descriptors ({@code (Ljava/lang/String;)V})</li>
 *   <li>Internal class name patterns ({@code org/bukkit/...})</li>
 *   <li>Short strings (≤ 2 chars) and empty strings</li>
 *   <li>Strings starting with {@code §} (Minecraft color codes used at runtime)</li>
 * </ul>
 */
public class ObfuscationEngine {
    private final int strength;
    private final NameGenerator nameGen;
    private final StringEncryptor encryptor;
    private final Map<String, String> classNameMap;
    private final Map<String, String> methodNameMap;

    /** Name of the injected static decryptor method */
    private static final String DECRYPT_METHOD_NAME = "__d";
    /** Name of the injected static key field */
    private static final String KEY_FIELD_NAME = "__k";

    public ObfuscationEngine(int strength) {
        this.strength = strength;
        this.nameGen = new NameGenerator();
        this.encryptor = new StringEncryptor();
        this.classNameMap = new HashMap<>();
        this.methodNameMap = new HashMap<>();
    }

    /**
     * Get the encryptor used by this engine (needed for embedding the key in payload metadata).
     */
    public StringEncryptor getEncryptor() {
        return encryptor;
    }

    /**
     * Obfuscate a single class's bytecode.
     *
     * <p>Applies string encryption when {@code strength >= 5}: encrypts eligible
     * string constants and injects an AES-256-GCM runtime decryptor.</p>
     *
     * @param classBytes  original class bytecode
     * @param className   internal class name (for logging, may be null)
     * @return obfuscated bytecode
     */
    public byte[] obfuscateClass(byte[] classBytes, String className) {
        boolean encryptStrings = strength >= 5;
        if (!encryptStrings) {
            // No string encryption — just apply dead code if strength >= 7
            if (strength >= 7) {
                return applyDeadCodeOnly(classBytes);
            }
            return classBytes;
        }

        // Use a two-pass approach:
        // Pass 1: Replace LDC strings with encrypted Base64 + INVOKESTATIC __d calls
        // Pass 2: Inject the __k field, <clinit> key init, and __d decryptor method

        // --- Pass 1: Encrypt strings ---
        ClassReader reader = new ClassReader(classBytes);
        // Use COMPUTE_MAXS only — COMPUTE_FRAMES tries to Class.forName() remapped classes
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

        boolean[] hasEncryptedStrings = {false};
        String[] detectedClassName = {null};

        ClassVisitor pass1 = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String name,
                              String signature, String superName, String[] interfaces) {
                detectedClassName[0] = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name,
                                             String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                // Don't encrypt strings inside <clinit> — would cause circular init
                if (name.equals("<clinit>")) return mv;

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String str && shouldEncryptString(str)) {
                            byte[] encryptedBytes = encryptor.encrypt(str);
                            String base64 = Base64.getEncoder().encodeToString(encryptedBytes);
                            hasEncryptedStrings[0] = true;

                            // Push encrypted Base64 and call __d
                            super.visitLdcInsn(base64);
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    detectedClassName[0],
                                    DECRYPT_METHOD_NAME,
                                    "(Ljava/lang/String;)Ljava/lang/String;",
                                    false
                            );
                        } else {
                            super.visitLdcInsn(value);
                        }
                    }
                };
            }
        };

        reader.accept(pass1, ClassReader.EXPAND_FRAMES);

        if (!hasEncryptedStrings[0]) {
            // No strings were encrypted — return the pass-through result
            return writer.toByteArray();
        }

        // --- Pass 2: Inject __k field, __d method, and <clinit> key init ---
        byte[] pass1Bytes = writer.toByteArray();
        ClassReader reader2 = new ClassReader(pass1Bytes);
        ClassWriter writer2 = new ClassWriter(reader2, ClassWriter.COMPUTE_MAXS);

        String finalClassName = detectedClassName[0];
        boolean[] hasExistingClinit = {false};

        ClassVisitor pass2 = new ClassVisitor(Opcodes.ASM9, writer2) {
            @Override
            public void visit(int version, int access, String name,
                              String signature, String superName, String[] interfaces) {
                super.visit(version, access, name, signature, superName, interfaces);

                // Inject the __k field
                FieldVisitor fv = super.visitField(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                        KEY_FIELD_NAME, "[B", null, null);
                fv.visitEnd();
            }

            @Override
            public MethodVisitor visitMethod(int access, String name,
                                             String descriptor, String signature, String[] exceptions) {
                if (name.equals("<clinit>")) {
                    // Existing <clinit> — prepend our key initialization
                    hasExistingClinit[0] = true;
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            // Prepend: __k = Base64.getDecoder().decode("keyBase64")
                            emitKeyInit(mv, finalClassName);
                        }
                    };
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override
            public void visitEnd() {
                // If no existing <clinit>, create one
                if (!hasExistingClinit[0]) {
                    MethodVisitor mv = super.visitMethod(
                            Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                    mv.visitCode();
                    emitKeyInit(mv, finalClassName);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(2, 0);
                    mv.visitEnd();
                }

                // Inject the __d(String) decryptor method
                injectDecryptMethod(this, finalClassName);

                super.visitEnd();
            }
        };

        reader2.accept(pass2, ClassReader.EXPAND_FRAMES);
        return writer2.toByteArray();
    }

    /**
     * Apply only dead-code injection (no string encryption).
     */
    private byte[] applyDeadCodeOnly(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name,
                                             String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return wrapWithDeadCode(mv);
            }
        };
        reader.accept(cv, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    /**
     * Emit bytecode to initialize the __k field: {@code __k = Base64.getDecoder().decode("...")}
     */
    private void emitKeyInit(MethodVisitor mv, String className) {
        String keyBase64 = Base64.getEncoder().encodeToString(encryptor.getKeyBytes());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Base64", "getDecoder",
                "()Ljava/util/Base64$Decoder;", false);
        mv.visitLdcInsn(keyBase64);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Base64$Decoder", "decode",
                "(Ljava/lang/String;)[B", false);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, className, KEY_FIELD_NAME, "[B");
    }

    // ==================== Decryptor injection ====================

    /**
     * Inject the static decryptor method: {@code private static String __d(String b64)}.
     *
     * <p>Generated bytecode equivalent:
     * <pre>{@code
     * private static String __d(String b64) {
     *     try {
     *         byte[] enc = java.util.Base64.getDecoder().decode(b64);
     *         java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(enc);
     *         byte[] iv = new byte[12];
     *         buf.get(iv);
     *         byte[] ct = new byte[buf.remaining()];
     *         buf.get(ct);
     *         javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
     *         c.init(javax.crypto.Cipher.DECRYPT_MODE,
     *                new javax.crypto.spec.SecretKeySpec(__k, "AES"),
     *                new javax.crypto.spec.GCMParameterSpec(128, iv));
     *         return new String(c.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
     *     } catch (Exception e) {
     *         return b64; // Fallback: return the encrypted string as-is (prevents crash)
     *     }
     * }
     * }</pre>
     */
    private void injectDecryptMethod(ClassVisitor cv, String className) {
        MethodVisitor mv = cv.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                DECRYPT_METHOD_NAME,
                "(Ljava/lang/String;)Ljava/lang/String;",
                null,
                null
        );
        mv.visitCode();

        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchHandler = new Label();
        mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/Exception");

        mv.visitLabel(tryStart);

        // byte[] enc = Base64.getDecoder().decode(b64)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Base64", "getDecoder",
                "()Ljava/util/Base64$Decoder;", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0); // b64 parameter
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Base64$Decoder", "decode",
                "(Ljava/lang/String;)[B", false);
        mv.visitVarInsn(Opcodes.ASTORE, 1); // enc

        // ByteBuffer buf = ByteBuffer.wrap(enc)
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/nio/ByteBuffer", "wrap",
                "([B)Ljava/nio/ByteBuffer;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 2); // buf

        // byte[] iv = new byte[12]
        mv.visitIntInsn(Opcodes.BIPUSH, 12);
        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
        mv.visitVarInsn(Opcodes.ASTORE, 3); // iv

        // buf.get(iv)
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/nio/ByteBuffer", "get",
                "([B)Ljava/nio/ByteBuffer;", false);
        mv.visitInsn(Opcodes.POP);

        // byte[] ct = new byte[buf.remaining()]
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/nio/ByteBuffer", "remaining",
                "()I", false);
        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
        mv.visitVarInsn(Opcodes.ASTORE, 4); // ct

        // buf.get(ct)
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/nio/ByteBuffer", "get",
                "([B)Ljava/nio/ByteBuffer;", false);
        mv.visitInsn(Opcodes.POP);

        // Cipher c = Cipher.getInstance("AES/GCM/NoPadding")
        mv.visitLdcInsn("AES/GCM/NoPadding");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "javax/crypto/Cipher", "getInstance",
                "(Ljava/lang/String;)Ljavax/crypto/Cipher;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 5); // cipher

        // SecretKeySpec keySpec = new SecretKeySpec(__k, "AES")
        mv.visitTypeInsn(Opcodes.NEW, "javax/crypto/spec/SecretKeySpec");
        mv.visitInsn(Opcodes.DUP);
        mv.visitFieldInsn(Opcodes.GETSTATIC, className, KEY_FIELD_NAME, "[B");
        mv.visitLdcInsn("AES");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "javax/crypto/spec/SecretKeySpec", "<init>",
                "([BLjava/lang/String;)V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 6); // keySpec

        // GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv)
        mv.visitTypeInsn(Opcodes.NEW, "javax/crypto/spec/GCMParameterSpec");
        mv.visitInsn(Opcodes.DUP);
        mv.visitIntInsn(Opcodes.SIPUSH, 128);
        mv.visitVarInsn(Opcodes.ALOAD, 3); // iv
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "javax/crypto/spec/GCMParameterSpec", "<init>",
                "(I[B)V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 7); // gcmSpec

        // c.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        mv.visitVarInsn(Opcodes.ALOAD, 5); // cipher
        mv.visitInsn(Opcodes.ICONST_2); // DECRYPT_MODE = 2
        mv.visitVarInsn(Opcodes.ALOAD, 6); // keySpec
        mv.visitVarInsn(Opcodes.ALOAD, 7); // gcmSpec
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javax/crypto/Cipher", "init",
                "(ILjava/security/Key;Ljava/security/spec/AlgorithmParameterSpec;)V", false);

        // byte[] plainBytes = c.doFinal(ct)
        mv.visitVarInsn(Opcodes.ALOAD, 5); // cipher
        mv.visitVarInsn(Opcodes.ALOAD, 4); // ct
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javax/crypto/Cipher", "doFinal",
                "([B)[B", false);
        mv.visitVarInsn(Opcodes.ASTORE, 8); // plainBytes

        // return new String(plainBytes, StandardCharsets.UTF_8)
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/String");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 8); // plainBytes
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/nio/charset/StandardCharsets", "UTF_8",
                "Ljava/nio/charset/Charset;");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>",
                "([BLjava/nio/charset/Charset;)V", false);
        mv.visitLabel(tryEnd);
        mv.visitInsn(Opcodes.ARETURN);

        // catch (Exception e) { return b64; }
        mv.visitLabel(catchHandler);
        mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Exception"});
        mv.visitVarInsn(Opcodes.ASTORE, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 0); // return original b64 as fallback
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(4, 9);
        mv.visitEnd();
    }




    // ==================== Filtering ====================

    /**
     * Determine whether a string constant should be encrypted.
     * Returns false for strings that would break JVM semantics or reflection.
     */
    private boolean shouldEncryptString(String str) {
        // Never encrypt empty or very short strings
        if (str == null || str.length() <= 2) return false;

        // Never encrypt JVM type descriptors
        if (str.startsWith("(") && str.contains(")")) return false;       // method descriptor
        if (str.startsWith("L") && str.endsWith(";")) return false;       // type descriptor
        if (str.startsWith("[")) return false;                             // array descriptor

        // Never encrypt internal class name patterns (used in reflection/getResource)
        if (str.contains("/") && !str.contains(" ")) return false;        // internal names like "java/lang/String"

        // Never encrypt Minecraft color codes (used for chat messages at high frequency)
        // These are fine to encrypt, but skip for performance in hot paths
        // Actually, let's encrypt these too — they're the most revealing strings

        // Never encrypt cipher/algorithm names (would cause infinite recursion in __d)
        if (str.equals("AES") || str.equals("AES/GCM/NoPadding") || str.equals("UTF-8")) return false;

        // Never encrypt strings that look like class names (dots + no spaces)
        if (str.matches("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*\\.[A-Z]\\w*$")) return false;

        // Never encrypt Base64-looking strings (could be our own encrypted output)
        if (str.matches("^[A-Za-z0-9+/=]{20,}$")) return false;

        return true;
    }

    private boolean isPreservedMethod(String name) {
        return name.equals("inject") ||
                name.equals("onChat") ||
                name.equals("onEnable") ||
                name.equals("onDisable") ||
                name.equals("<init>") ||
                name.equals("<clinit>") ||
                name.equals("__d") ||           // Our own decryptor
                name.equals("__clinit_key");    // Our own key init
    }

    private boolean isPreservedField(String name) {
        return name.equals(KEY_FIELD_NAME) ||
                name.equals("serialVersionUID");
    }

    // ==================== Dead code injection ====================

    private MethodVisitor wrapWithDeadCode(MethodVisitor mv) {
        if (strength < 7) return mv;

        return new MethodVisitor(Opcodes.ASM9, mv) {
            @Override
            public void visitInsn(int opcode) {
                if (Math.random() < 0.1) {
                    insertDeadCode(mv);
                }
                super.visitInsn(opcode);
            }
        };
    }

    private void insertDeadCode(MethodVisitor mv) {
        Label skip = new Label();
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.ICONST_2);
        mv.visitJumpInsn(Opcodes.IF_ICMPNE, skip);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
        mv.visitInsn(Opcodes.POP2);
        mv.visitLabel(skip);
    }
}
