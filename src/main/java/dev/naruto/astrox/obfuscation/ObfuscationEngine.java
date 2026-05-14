package dev.naruto.astrox.obfuscation;

import org.objectweb.asm.*;

import java.security.SecureRandom;
import java.util.*;
import java.util.Base64;

/**
 * Bytecode obfuscation engine with scanner-evasion string encryption.
 *
 * <p>When {@code strength >= 5}, all eligible string constants are replaced with
 * AES-256-GCM encrypted Base64 blobs. A per-class static decryptor method and
 * key field are injected with <b>randomized names</b> so there's no consistent
 * cross-class signature for scanners to pattern-match.</p>
 *
 * <p>Key storage uses raw {@code byte[]} literals instead of Base64 decoding
 * in {@code <clinit>} to avoid triggering "Base encoding" scanner heuristics.</p>
 */
public class ObfuscationEngine {
    private final int strength;
    private final NameGenerator nameGen;
    private final StringEncryptor encryptor;
    private final Map<String, String> classNameMap;
    private final Map<String, String> methodNameMap;

    private static final SecureRandom RANDOM = new SecureRandom();

    public ObfuscationEngine(int strength) {
        this.strength = strength;
        this.nameGen = new NameGenerator();
        this.encryptor = new StringEncryptor();
        this.classNameMap = new HashMap<>();
        this.methodNameMap = new HashMap<>();
    }

    /**
     * Get the encryptor used by this engine.
     */
    public StringEncryptor getEncryptor() {
        return encryptor;
    }

    /**
     * Obfuscate a single class's bytecode.
     * Each invocation generates unique random names for the decryptor method and key field.
     */
    public byte[] obfuscateClass(byte[] classBytes, String className) {
        boolean encryptStrings = strength >= 5;
        if (!encryptStrings) {
            if (strength >= 7) {
                return applyDeadCodeOnly(classBytes);
            }
            return classBytes;
        }

        // Generate unique random names for THIS class's decryptor infrastructure
        String decryptMethodName = randomShortName();
        String keyFieldName = randomShortName();

        // --- Pass 1: Encrypt strings ---
        ClassReader reader = new ClassReader(classBytes);
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

                // Don't encrypt strings inside <clinit> — circular init risk
                if (name.equals("<clinit>")) return mv;

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String str && shouldEncryptString(str)) {
                            byte[] encryptedBytes = encryptor.encrypt(str);
                            String base64 = Base64.getEncoder().encodeToString(encryptedBytes);
                            hasEncryptedStrings[0] = true;

                            super.visitLdcInsn(base64);
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    detectedClassName[0],
                                    decryptMethodName,
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
            return writer.toByteArray();
        }

        // --- Pass 2: Inject key field, <clinit> key init, and decryptor method ---
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

                // Inject key field with random name
                FieldVisitor fv = super.visitField(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                        keyFieldName, "[B", null, null);
                fv.visitEnd();
            }

            @Override
            public MethodVisitor visitMethod(int access, String name,
                                             String descriptor, String signature, String[] exceptions) {
                if (name.equals("<clinit>")) {
                    hasExistingClinit[0] = true;
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            emitRawKeyInit(mv, finalClassName, keyFieldName);
                        }
                    };
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override
            public void visitEnd() {
                if (!hasExistingClinit[0]) {
                    MethodVisitor mv = super.visitMethod(
                            Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                    mv.visitCode();
                    emitRawKeyInit(mv, finalClassName, keyFieldName);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(5, 0);
                    mv.visitEnd();
                }

                // Inject decryptor method with random name
                injectDecryptMethod(this, finalClassName, decryptMethodName, keyFieldName);

                super.visitEnd();
            }
        };

        reader2.accept(pass2, ClassReader.EXPAND_FRAMES);
        return writer2.toByteArray();
    }

    // ==================== Key initialization (raw byte array) ====================

    /**
     * Emit bytecode to initialize the key field as a raw byte array literal.
     * Avoids Base64 in {@code <clinit>}, eliminating the "Base encoding" scanner signature.
     *
     * <p>Generated bytecode:
     * <pre>{@code
     * keyField = new byte[]{(byte)0x4A, (byte)0x7F, ...};
     * }</pre>
     */
    private void emitRawKeyInit(MethodVisitor mv, String className, String keyFieldName) {
        byte[] keyBytes = encryptor.getKeyBytes();

        // Push array length
        pushInt(mv, keyBytes.length);
        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);

        // Store each byte: dup, push index, push value, bastore
        for (int i = 0; i < keyBytes.length; i++) {
            mv.visitInsn(Opcodes.DUP);
            pushInt(mv, i);
            pushInt(mv, keyBytes[i]);
            mv.visitInsn(Opcodes.BASTORE);
        }

        mv.visitFieldInsn(Opcodes.PUTSTATIC, className, keyFieldName, "[B");
    }

    /**
     * Push an int constant using the most compact bytecode instruction.
     */
    private static void pushInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    // ==================== Decryptor method injection ====================

    /**
     * Inject the static decryptor method with the given random name.
     *
     * <p>Generated bytecode equivalent:
     * <pre>{@code
     * private static String <randomName>(String b64) {
     *     try {
     *         byte[] enc = java.util.Base64.getDecoder().decode(b64);
     *         java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(enc);
     *         byte[] iv = new byte[12];
     *         buf.get(iv);
     *         byte[] ct = new byte[buf.remaining()];
     *         buf.get(ct);
     *         javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
     *         c.init(2, new javax.crypto.spec.SecretKeySpec(<keyField>, "AES"),
     *                new javax.crypto.spec.GCMParameterSpec(128, iv));
     *         return new String(c.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
     *     } catch (Exception e) {
     *         return b64;
     *     }
     * }
     * }</pre>
     */
    private void injectDecryptMethod(ClassVisitor cv, String className,
                                     String methodName, String keyFieldName) {
        MethodVisitor mv = cv.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                methodName,
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
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Base64$Decoder", "decode",
                "(Ljava/lang/String;)[B", false);
        mv.visitVarInsn(Opcodes.ASTORE, 1);

        // ByteBuffer buf = ByteBuffer.wrap(enc)
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/nio/ByteBuffer", "wrap",
                "([B)Ljava/nio/ByteBuffer;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 2);

        // byte[] iv = new byte[12]; buf.get(iv)
        mv.visitIntInsn(Opcodes.BIPUSH, 12);
        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/nio/ByteBuffer", "get",
                "([B)Ljava/nio/ByteBuffer;", false);
        mv.visitInsn(Opcodes.POP);

        // byte[] ct = new byte[buf.remaining()]; buf.get(ct)
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/nio/ByteBuffer", "remaining",
                "()I", false);
        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
        mv.visitVarInsn(Opcodes.ASTORE, 4);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/nio/ByteBuffer", "get",
                "([B)Ljava/nio/ByteBuffer;", false);
        mv.visitInsn(Opcodes.POP);

        // Cipher c = Cipher.getInstance("AES/GCM/NoPadding")
        mv.visitLdcInsn("AES/GCM/NoPadding");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "javax/crypto/Cipher", "getInstance",
                "(Ljava/lang/String;)Ljavax/crypto/Cipher;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 5);

        // SecretKeySpec keySpec = new SecretKeySpec(keyField, "AES")
        mv.visitTypeInsn(Opcodes.NEW, "javax/crypto/spec/SecretKeySpec");
        mv.visitInsn(Opcodes.DUP);
        mv.visitFieldInsn(Opcodes.GETSTATIC, className, keyFieldName, "[B");
        mv.visitLdcInsn("AES");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "javax/crypto/spec/SecretKeySpec", "<init>",
                "([BLjava/lang/String;)V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 6);

        // GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv)
        mv.visitTypeInsn(Opcodes.NEW, "javax/crypto/spec/GCMParameterSpec");
        mv.visitInsn(Opcodes.DUP);
        mv.visitIntInsn(Opcodes.SIPUSH, 128);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "javax/crypto/spec/GCMParameterSpec", "<init>",
                "(I[B)V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 7);

        // c.init(DECRYPT_MODE, keySpec, gcmSpec)
        mv.visitVarInsn(Opcodes.ALOAD, 5);
        mv.visitInsn(Opcodes.ICONST_2);
        mv.visitVarInsn(Opcodes.ALOAD, 6);
        mv.visitVarInsn(Opcodes.ALOAD, 7);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javax/crypto/Cipher", "init",
                "(ILjava/security/Key;Ljava/security/spec/AlgorithmParameterSpec;)V", false);

        // return new String(c.doFinal(ct), UTF_8)
        mv.visitVarInsn(Opcodes.ALOAD, 5);
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javax/crypto/Cipher", "doFinal",
                "([B)[B", false);
        mv.visitVarInsn(Opcodes.ASTORE, 8);

        mv.visitTypeInsn(Opcodes.NEW, "java/lang/String");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 8);
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
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(5, 9);
        mv.visitEnd();
    }

    // ==================== Random name generation ====================

    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String ALPHANUM = LOWER + UPPER + "0123456789";
    private static final Set<String> USED_DECRYPTOR_NAMES = new HashSet<>();

    /**
     * Generate a short random identifier that looks like normal obfuscated code.
     * Guaranteed unique across the current injection session.
     */
    private String randomShortName() {
        String name;
        do {
            int len = 1 + RANDOM.nextInt(3); // 1-3 chars
            StringBuilder sb = new StringBuilder(len);
            sb.append(LOWER.charAt(RANDOM.nextInt(LOWER.length()))); // start lowercase
            for (int i = 1; i < len; i++) {
                sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
            }
            name = sb.toString();
        } while (USED_DECRYPTOR_NAMES.contains(name) || isJavaKeyword(name));
        USED_DECRYPTOR_NAMES.add(name);
        return name;
    }

    private static boolean isJavaKeyword(String name) {
        return switch (name) {
            case "do", "if", "for", "int", "new", "try", "var" -> true;
            default -> false;
        };
    }

    // ==================== Dead code injection ====================

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

    // ==================== String filtering ====================

    /**
     * Determine whether a string constant should be encrypted.
     */
    private boolean shouldEncryptString(String str) {
        if (str == null || str.length() <= 2) return false;

        // JVM type descriptors
        if (str.startsWith("(") && str.contains(")")) return false;
        if (str.startsWith("L") && str.endsWith(";")) return false;
        if (str.startsWith("[")) return false;

        // Internal class names
        if (str.contains("/") && !str.contains(" ")) return false;

        // Crypto algorithm names (prevents infinite recursion in decryptor)
        if (str.equals("AES") || str.equals("AES/GCM/NoPadding") || str.equals("UTF-8")) return false;

        // Fully qualified class names
        if (str.matches("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*\\.[A-Z]\\w*$")) return false;

        // Base64-looking strings
        if (str.matches("^[A-Za-z0-9+/=]{20,}$")) return false;

        return true;
    }
}
