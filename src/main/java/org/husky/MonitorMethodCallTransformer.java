package org.husky;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Iterator;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

public class MonitorMethodCallTransformer implements ClassFileTransformer {
    private static final String METHOD_CALL_CONTEXT_NAME = "org/husky/MethodCallContext";

    private static final ThreadLocal<Boolean> transforming = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    public byte[] transform(ClassLoader loader,
                     String className,
                     Class<?> classBeingRedefined,
                     ProtectionDomain protectionDomain,
                     byte[] classfileBuffer)
            throws IllegalClassFormatException {
        try {
            if (className.startsWith("java/") ||
                    className.startsWith("javax") ||
                    className.startsWith("jdk") ||
                    className.startsWith("sun") ||
                    className.startsWith("com/sun") ||
                    className.startsWith("org/husky/"))
                return classfileBuffer;

            if (transforming.get())
                return classfileBuffer;

            transforming.set(Boolean.TRUE);

            ClassNode classNode = byte2ClassNode(classfileBuffer);

            ClassWriter cw = new ClassWriter(0);
            classNode.accept(cw);

            transforming.set(Boolean.FALSE);

            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return classfileBuffer;
        }
    }

    private ClassNode byte2ClassNode(byte[] classfileBuffer) {
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassNode classNode = new ClassNode(ASM7);

        cr.accept(classNode, ClassReader.SKIP_FRAMES);

        for (MethodNode methodNode : classNode.methods) {
            injectInspector(methodNode);
        }

        return classNode;
    }

    private void injectInspector(MethodNode methodNode) {
        InsnList insnList = methodNode.instructions;
        Iterator<AbstractInsnNode> iterator = insnList.iterator();
        int lineNumber = -1;
        TypeInsnNode newInsn = null;
        LabelNode firstLabel = null, lastLabel = null;
        boolean hasMethodInsn = false;
        while (iterator.hasNext()) {
            AbstractInsnNode insnNode = iterator.next();
            int opcode = insnNode.getOpcode();

            if (insnNode instanceof LabelNode) {
                if (firstLabel == null) {
                    firstLabel = (LabelNode) insnNode;
                }
                lastLabel = (LabelNode) insnNode;
                continue;
            }

            if (insnNode instanceof LineNumberNode) {
                lineNumber = ((LineNumberNode) insnNode).line;
                continue;
            }

            if (insnNode instanceof TypeInsnNode) {
                newInsn = (TypeInsnNode) insnNode;
                continue;
            }

            if (insnNode instanceof MethodInsnNode) {
                hasMethodInsn = true;
                MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;

                InsnList il = recordContextInsnList(methodInsnNode.owner, methodInsnNode.name, opcode, lineNumber);

                if ("<init>".equals(methodInsnNode.name) && newInsn != null) {
                    if (newInsn.getPrevious() == null) {
                        insnList.insert(il);
                    } else {
                        insnList.insert(newInsn.getPrevious(), il);
                    }
                    newInsn = null;
                } else {
                    if (insnNode.getPrevious() == null) {
                        insnList.insert(il);
                    } else {
                        insnList.insert(insnNode.getPrevious(), il);
                    }
                }

                il = destroyContextInsnList();
                insnList.insert(insnNode, il);

                continue;
            }

            if (insnNode instanceof InvokeDynamicInsnNode) {
                hasMethodInsn = true;
                InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) insnNode;
                InsnList il = recordContextInsnList("", invokeDynamicInsnNode.name, opcode, lineNumber);
                if (insnNode.getPrevious() == null) {
                    insnList.insert(il);
                } else {
                    insnList.insert(insnNode.getPrevious(), il);
                }

                il = destroyContextInsnList();
                insnList.insert(insnNode, il);

                continue;
            }
        }

        if (hasMethodInsn)
            methodNode.maxStack += 4;

        if (methodNode.tryCatchBlocks.size() == 0)
            return;

        boolean hasExceptionHandler = false;
        for (TryCatchBlockNode tryCatchBlock : methodNode.tryCatchBlocks) {
            if (tryCatchBlock.type == null) continue;

            hasExceptionHandler = true;
            AbstractInsnNode insnNode = tryCatchBlock.handler.getNext();
            while (insnNode instanceof LineNumberNode ||
                    insnNode instanceof FrameNode ||
                    insnNode instanceof LabelNode)
                insnNode = insnNode.getNext();

            InsnList il = new InsnList();
            il.add(new VarInsnNode(ALOAD, methodNode.maxLocals));
            il.add(new MethodInsnNode(INVOKESTATIC, METHOD_CALL_CONTEXT_NAME,
                    "setCurrentContext", "(L" + METHOD_CALL_CONTEXT_NAME + ";)V", false));
            insnList.insert(insnNode, il);
        }

        if (hasExceptionHandler) {
            List<LocalVariableNode> variables = methodNode.localVariables;
            variables.add(new LocalVariableNode("__methodContext__",
                    "L" + METHOD_CALL_CONTEXT_NAME + ";",
                    null,
                    firstLabel,
                    lastLabel,
                    methodNode.maxLocals));

            InsnList il = new InsnList();
            il.add(new MethodInsnNode(INVOKESTATIC, METHOD_CALL_CONTEXT_NAME,
                    "getCurrentContext", "()L" + METHOD_CALL_CONTEXT_NAME + ";", false));
            il.add(new VarInsnNode(ASTORE, methodNode.maxLocals));
            insnList.insert(il);

            methodNode.maxLocals++;
        }
    }

    private InsnList recordContextInsnList(String owner, String name, int opcode, int line) {
        InsnList il = new InsnList();
        il.add(new LdcInsnNode(owner));
        il.add(new LdcInsnNode(name));
        il.add(new LdcInsnNode(MethodCallContext.getMethodType(opcode, name)));
        il.add(new LdcInsnNode(Integer.valueOf(line)));
        il.add(new MethodInsnNode(
                INVOKESTATIC,
                METHOD_CALL_CONTEXT_NAME,
                "createContext",
                "(Ljava/lang/String;Ljava/lang/String;II)L" + METHOD_CALL_CONTEXT_NAME + ";",
                false));

        il.add(new MethodInsnNode(INVOKEVIRTUAL, METHOD_CALL_CONTEXT_NAME,
                "record", "()V", false));

        return il;
    }

    private InsnList destroyContextInsnList() {
        InsnList il = new InsnList();
        il.add(new MethodInsnNode(INVOKESTATIC, METHOD_CALL_CONTEXT_NAME,
                "destroyCurrentContext", "()V", false));

        return il;
    }

    public static void main(String[] args) throws IOException, NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {
        String classFile;
        boolean debug = false;
        if ("-debug".equals(args[0])) {
            classFile = args[1];
            debug = true;
        } else {
            classFile = args[0];
        }

        ClassReader cr = new ClassReader(new FileInputStream(classFile));
        ClassWriter cw = new ClassWriter(cr, 0);
        cr.accept(cw, ClassReader.SKIP_FRAMES);
        byte[] classfileBuffer = cw.toByteArray();

        MonitorMethodCallTransformer transformer = new MonitorMethodCallTransformer();

        if (debug) {
            ClassWriter classWriter = new ClassWriter(/*ClassWriter.COMPUTE_FRAMES | */ 0);
            ClassVisitor cv = new CheckClassAdapter(classWriter);

            ClassNode classNode = transformer.byte2ClassNode(classfileBuffer);
            classNode.accept(cv);

            classfileBuffer = classWriter.toByteArray();
            class TransformerClassLoader extends ClassLoader {
                public Class defineClass(String name, byte[] classfileBuffer) {
                    return defineClass(name, classfileBuffer, 0, classfileBuffer.length);
                }
            }
            Class clazz = (new TransformerClassLoader()).defineClass("CallGraphTest", classfileBuffer);
            Method method = clazz.getMethod("main", String[].class);
            method.invoke(null, (Object) args);
        } else {
            ClassVisitor cv = new TraceClassVisitor(cw, new PrintWriter(System.out));

            ClassNode classNode = transformer.byte2ClassNode(classfileBuffer);
            classNode.accept(cv);
        }
    }
}
