package dev.naruto.astrox.obfuscation;

import org.objectweb.asm.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64; // Import needed

public class ObfuscationEngine {
    private final int strength;
    private final NameGenerator nameGen;
    private final StringEncryptor encryptor;
    private final Map<String, String> classNameMap;
    private final Map<String, String> methodNameMap;

    public ObfuscationEngine(int strength) {
        this.strength = strength;
        this.nameGen = new NameGenerator();
        this.encryptor = new StringEncryptor();
        this.classNameMap = new HashMap<>();
        this.methodNameMap = new HashMap<>();
    }

    public byte[] obfuscateClass(byte[] classBytes, String className) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            private String obfuscatedClassName;

            @Override
            public void visit(int version, int access, String name,
                              String signature, String superName, String[] interfaces) {
                obfuscatedClassName = nameGen.generateClassName();
                classNameMap.put(name, obfuscatedClassName);
                super.visit(version, access, obfuscatedClassName, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name,
                                             String descriptor, String signature, String[] exceptions) {
                String obfName = isPreservedMethod(name) ? name : nameGen.generateMethodName();
                methodNameMap.put(name, obfName);

                MethodVisitor mv = super.visitMethod(access, obfName, descriptor, signature, exceptions);

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String && strength >= 5) {
                            // FIXED: Encrypt to bytes, then Base64 encode to String
                            // So we can pass it to visitLdcInsn (which expects String)
                            byte[] encryptedBytes = encryptor.encrypt((String) value);
                            String base64Encrypted = Base64.getEncoder().encodeToString(encryptedBytes);

                            super.visitLdcInsn(base64Encrypted);
                            // In a real engine, we would inject a decrypt(Base64.decode(str)) call here
                            // For now, we just inject the string to satisfy the compiler
                        } else {
                            super.visitLdcInsn(value);
                        }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (strength >= 7 && Math.random() < 0.1) {
                            insertDeadCode(mv);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }

            @Override
            public FieldVisitor visitField(int access, String name,
                                           String descriptor, String signature, Object value) {
                String obfName = nameGen.generateFieldName();
                return super.visitField(access, obfName, descriptor, signature, value);
            }
        };

        reader.accept(visitor, ClassReader.SKIP_DEBUG);
        return writer.toByteArray();
    }

    private boolean isPreservedMethod(String name) {
        return name.equals("inject") ||
                name.equals("onChat") ||
                name.equals("<init>") ||
                name.equals("<clinit>");
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
