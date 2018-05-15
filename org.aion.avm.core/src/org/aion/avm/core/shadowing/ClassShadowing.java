package org.aion.avm.core.shadowing;

import org.aion.avm.core.util.Assert;
import org.objectweb.asm.*;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility for replacing class refs
 */
public class ClassShadowing extends ClassVisitor {

    // TODO: Yulong, refactor this class to implement class visitor chain

    private static final String JAVA_LANG = "java/lang";
    private static final String JAVA_LANG_SHADOW = "org/aion/avm/java/lang";

    private static final String METHOD_PREFIX = "avm_";

    public ClassShadowing(ClassVisitor visitor) {
        super(Opcodes.ASM6, visitor);
    }

    /**
     * Modify the class reference if the type starts with {@link #JAVA_LANG}.
     *
     * @param type
     * @return
     */
    private static String replaceType(String type) {
        return type.startsWith(JAVA_LANG) ? JAVA_LANG_SHADOW + type.substring(JAVA_LANG.length()) : type;
    }

    /**
     * Modify the method reference if the owner type starts with {@link #JAVA_LANG}.
     *
     * @param type
     * @return
     */
    private static String replaceMethodName(String type, String methodName) {
        if (type.startsWith(JAVA_LANG)) {
            return methodName.equals("<init>") ? methodName : METHOD_PREFIX + methodName;
        } else {
            return methodName;
        }
    }

    /**
     * Modify the method descriptor if it uses any type that starts with {@link #JAVA_LANG}
     *
     * @param methodDescriptor
     * @return
     */
    private static String replaceMethodDescriptor(String methodDescriptor) {
        StringBuilder sb = new StringBuilder();

        int from = 0;
        while (from < methodDescriptor.length()) {
            from = readType(sb, methodDescriptor, from);
        }

        return sb.toString();
    }

    private static int readType(StringBuilder sb, String methodDescriptor, int from) {
        char c = methodDescriptor.charAt(from);

        switch (c) {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 'V':
                sb.append(c);
                return from + 1;
            case 'L': {
                sb.append(c);
                int idx = methodDescriptor.indexOf(';', from);
                sb.append(replaceType(methodDescriptor.substring(from + 1, idx)));
                sb.append(';');
                return idx + 1;
            }
            case '[': {
                sb.append(c);
                return readType(sb, methodDescriptor, from + 1);
            }
            case '(': {
                sb.append(c);
                int idx = methodDescriptor.indexOf(')', from);
                sb.append(replaceMethodDescriptor(methodDescriptor.substring(from + 1, idx)));
                sb.append(')');
                return idx + 1;
            }
            default:
                throw new RuntimeException("Failed to parse type: descriptor = " + methodDescriptor + ", from = " + from);
        }
    }


    public static byte[] replaceJavaLang(String runtimeClassName, byte[] classFile) {
        ClassReader in = new ClassReader(classFile);
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM6, out) {

            @Override
            public void visit(
                    final int version,
                    final int access,
                    final String name,
                    final String signature,
                    final String superName,
                    final String[] interfaces) {

                assert (!name.startsWith(JAVA_LANG));

                String newSuperName = replaceType(superName);
                String[] newInterfaces = Stream.of(interfaces).map(i -> replaceType(i)).collect(Collectors.toList()).stream().toArray(String[]::new);
                // Just pass in a null signature, instead of updating it (JVM spec 4.3.4: "This kind of type information is needed to support reflection and debugging, and by a Java compiler").
                out.visit(version, access, name, null, newSuperName, newInterfaces);
            }

            @Override
            public MethodVisitor visitMethod(
                    final int access,
                    final String name,
                    final String descriptor,
                    final String signature,
                    final String[] exceptions) {

                // Just pass in a null signature, instead of updating it (JVM spec 4.3.4: "This kind of type information is needed to support reflection and debugging, and by a Java compiler").
                MethodVisitor mv = super.visitMethod(access, name, replaceMethodDescriptor(descriptor), null, exceptions);

                return new MethodVisitor(Opcodes.ASM6, mv) {
                    @Override
                    public void visitMethodInsn(
                            final int opcode,
                            final String owner,
                            final String name,
                            final String descriptor,
                            final boolean isInterface) {
                        mv.visitMethodInsn(opcode, replaceType(owner), replaceMethodName(owner, name), replaceMethodDescriptor(descriptor), isInterface);
                    }

                    @Override
                    public void visitTypeInsn(final int opcode, final String type) {
                        mv.visitTypeInsn(opcode, replaceType(type));
                    }

                    @Override
                    public void visitLdcInsn(final Object value) {
                        // We will default to passing the value through since only 1 case does anything different.
                        Object valueToWrite = value;
                        boolean shouldWrapAsString = false;
                        boolean shouldWrapAsClass = false;
                        
                        if (value instanceof Type) {
                            Type type = (Type) value;
                            // TODO: how to deal with METHOD and HANDLE?
                            switch (type.getSort()) {
                                case Type.ARRAY: {
                                    Assert.unimplemented("Array type sort doesn't seem to appear in this usage.");
                                    break;
                                }
                                case Type.BOOLEAN: 
                                case Type.BYTE:
                                case Type.CHAR:
                                case Type.DOUBLE:
                                case Type.FLOAT:
                                case Type.INT:
                                case Type.LONG:
                                case Type.SHORT: {
                                    // These primitive require no special handling - just emit the instruction.
                                    break;
                                }
                                case Type.METHOD: {
                                    // The method constant sort should only show up related to either invokedynamic or reflection and we don't support those cases.
                                    Assert.unreachable("Method constants cannot be loaded should have been filtered earlier.");
                                    break;
                                }
                                case Type.OBJECT: {
                                    // This is the interesting case where we might need to replace the descriptor.
                                    valueToWrite = Type.getType(replaceMethodDescriptor(type.getDescriptor()));
                                    // This is also the case where we want to wrap this as a class (since strings go through their own path).
                                    shouldWrapAsClass = true;
                                    break;
                                }
                                case Type.VOID: {
                                    Assert.unreachable("Void constants cannot be loaded.");
                                    break;
                                }
                                default:
                                    Assert.unreachable("Unknown type: " + type.getSort());
                            }
                        } else if (value instanceof String) {
                            shouldWrapAsString = true;
                        }
                        
                        // All paths emit the instruction.
                        mv.visitLdcInsn(valueToWrite);
                        
                        // If we need to wrap this, call out to our static helper.
                        if (shouldWrapAsString) {
                            String methodName = "wrapAsString";
                            String methodDescriptor = "(Ljava/lang/String;)Lorg/aion/avm/java/lang/String;";
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, runtimeClassName, methodName, methodDescriptor, false);
                        } else if (shouldWrapAsClass) {
                            String methodName = "wrapAsClass";
                            String methodDescriptor = "(Ljava/lang/Class;)Lorg/aion/avm/java/lang/Class;";
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, runtimeClassName, methodName, methodDescriptor, false);
                        }
                    }
                };
            }
        };
        in.accept(visitor, ClassReader.SKIP_DEBUG);

        return out.toByteArray();
    }
}
