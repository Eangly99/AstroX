package dev.naruto.astrox.obfuscation;

import org.objectweb.asm.*;
import java.util.Map;

/**
 * Low-level ASM transformer for payload obfuscation.
 * Renames members and strips debug info.
 */
public class ClassMutator {
    private final Map<String, String> classMap;
    private final Map<String, String> methodMap;
    private final Map<String, String> fieldMap;

    public ClassMutator(Map<String, String> classMap,
                        Map<String, String> methodMap,
                        Map<String, String> fieldMap) {
        this.classMap = classMap;
        this.methodMap = methodMap;
        this.fieldMap = fieldMap;
    }

    public byte[] mutate(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                // Remap class name and superclass
                String newName = classMap.getOrDefault(name, name);
                String newSuper = classMap.getOrDefault(superName, superName);

                // Remap interfaces
                String[] newInterfaces = null;
                if (interfaces != null) {
                    newInterfaces = new String[interfaces.length];
                    for (int i = 0; i < interfaces.length; i++) {
                        newInterfaces[i] = classMap.getOrDefault(interfaces[i], interfaces[i]);
                    }
                }

                super.visit(version, access, newName, signature, newSuper, newInterfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                String newName = fieldMap.getOrDefault(name, name);
                String newDesc = remapDescriptor(descriptor);
                return super.visitField(access, newName, newDesc, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                String newName = methodMap.getOrDefault(name, name);
                String newDesc = remapDescriptor(descriptor);

                MethodVisitor mv = super.visitMethod(access, newName, newDesc, signature, exceptions);
                return new MethodAdapter(mv);
            }
        };

        // Skip debug info (SourceFile, LineNumberTable, LocalVariableTable)
        reader.accept(visitor, ClassReader.SKIP_DEBUG);
        return writer.toByteArray();
    }

    private String remapDescriptor(String desc) {
        // Simple regex replacement for class names in descriptors
        String result = desc;
        for (Map.Entry<String, String> entry : classMap.entrySet()) {
            result = result.replace("L" + entry.getKey() + ";", "L" + entry.getValue() + ";");
        }
        return result;
    }

    /**
     * Adapter to remap types inside method bodies
     */
    private class MethodAdapter extends MethodVisitor {
        public MethodAdapter(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            super.visitTypeInsn(opcode, classMap.getOrDefault(type, type));
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            super.visitFieldInsn(opcode,
                    classMap.getOrDefault(owner, owner),
                    fieldMap.getOrDefault(name, name),
                    remapDescriptor(descriptor));
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode,
                    classMap.getOrDefault(owner, owner),
                    methodMap.getOrDefault(name, name),
                    remapDescriptor(descriptor),
                    isInterface);
        }
    }
}
