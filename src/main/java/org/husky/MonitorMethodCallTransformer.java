package org.husky;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;
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
        if (transforming.get())
            return null;

        transforming.set(Boolean.TRUE);

        ClassNode classNode = byte2ClassNode(classfileBuffer);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(cw);

        transforming.set(Boolean.FALSE);

        return cw.toByteArray();
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
                MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;

                InsnList il = recordContextInsnList(methodInsnNode.owner, methodInsnNode.name, opcode, lineNumber);

                if ("<init>".equals(methodInsnNode.name) && newInsn != null) {
                    insnList.insert(newInsn.getPrevious(), il);
                    newInsn = null;
                } else {
                    insnList.insert(insnNode.getPrevious(), il);
                }

                il = destroyContextInsnList();
                insnList.insert(insnNode, il);

                continue;
            }

            if (insnNode instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) insnNode;
                InsnList il = recordContextInsnList("", invokeDynamicInsnNode.name, opcode, lineNumber);
                insnList.insert(insnNode.getPrevious(), il);
                il = destroyContextInsnList();
                insnList.insert(insnNode, il);

                continue;
            }
        }

        /*if (hasMethodInsn)
            methodNode.maxStack += 4;*/

        if (methodNode.tryCatchBlocks.size() == 0)
            return;

        boolean hasExceptionHandler = false;
        for (TryCatchBlockNode tryCatchBlock : methodNode.tryCatchBlocks) {
            if (tryCatchBlock.type == null) continue;

            if (!hasExceptionHandler) {
                /*methodNode.maxLocals++;*/
                hasExceptionHandler = true;
            }

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
        }
    }

    private InsnList recordContextInsnList(String owner, String name, int opcode, int line) {
        InsnList il = new InsnList();
        il.add(new LdcInsnNode(owner));
        il.add(new LdcInsnNode(name));
        il.add(new LdcInsnNode(MethodCallContext.getMethodType(opcode, name)));
        il.add(new LdcInsnNode(Integer.valueOf(line)));
        il.add(new MethodInsnNode(INVOKESTATIC, METHOD_CALL_CONTEXT_NAME,
                "createContext", "()L" + METHOD_CALL_CONTEXT_NAME + ";", false));

        il.add(new MethodInsnNode(INVOKEVIRTUAL, METHOD_CALL_CONTEXT_NAME,
                "record", "(L" + METHOD_CALL_CONTEXT_NAME + ";)V", false));

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

        ClassVisitor cv;
        if (debug) {
            cv = new ClassWriter(/*ClassWriter.COMPUTE_FRAMES | */ClassWriter.COMPUTE_MAXS);
        } else {
            cv = new TraceClassVisitor(cw, new PrintWriter(System.out));
        }
        ClassNode classNode = transformer.byte2ClassNode(classfileBuffer);
        classNode.accept(cv);

        if (!debug) return;

        classfileBuffer = ((ClassWriter) cv).toByteArray();
        class TransformerClassLoader extends ClassLoader {
            public Class defineClass(String name, byte[] classfileBuffer) {
                return defineClass(name, classfileBuffer, 0, classfileBuffer.length);
            }
        }
        Class clazz = (new TransformerClassLoader()).defineClass("CallGraphTest", classfileBuffer);
        Method method = clazz.getMethod("main", String[].class);
        method.invoke(null, (Object) args);
    }
}
