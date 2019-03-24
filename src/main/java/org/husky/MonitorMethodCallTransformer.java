package org.husky;

import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Iterator;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

public class MonitorMethodCallTransformer implements ClassFileTransformer {
    private static final String TRANSFORMER_CLASS = "org/husky/MonitorMethodCallTransformer";
    private static final String CALL_CONTEXT_CLASS = "org/husky/MethodCallContext";

    private boolean debug = false;
    private boolean instrumentJdkMethod = false;
    private String outputTransformedClass;
    private String traceTransformedClass;
    private String checkTransformedClass;

    private static MethodCallInstrumenter instrumenter;

    public MonitorMethodCallTransformer(AgentOption agentOption) {
        debug = agentOption.isDebug();
        instrumentJdkMethod = agentOption.isIncludeJdkMethod();
        outputTransformedClass = agentOption.getOutputTransformedClass();
        traceTransformedClass = agentOption.getTraceTransformedClass();
        checkTransformedClass = agentOption.getCheckTransformedClass();

        instrumenter = new MethodCallInstrumenter();

        instrumenter.setInstrumentJdkMethod(instrumentJdkMethod);
        instrumenter.setInstrumentThread(agentOption.getThread());
        instrumenter.setInstrumentStartPoint(agentOption.getCaptureStart());
        instrumenter.setInstrumentEndPoint(agentOption.getCaptureEnd());
        if (agentOption.getProcessor() != null)
            instrumenter.setProcessor(agentOption.getProcessor());
    }

    public byte[] transform(ClassLoader loader,
                     String className,
                     Class<?> classBeingRedefined,
                     ProtectionDomain protectionDomain,
                     byte[] classfileBuffer)
            throws IllegalClassFormatException {
        try {
            if (classBeingRedefined != null) {
                return null;
            }

            if (className.startsWith("org/husky/"))
                return null;

            if (!instrumentJdkMethod && isInstrumentJdkClass(className))
                return null;

            ClassNode classNode = injectInstrumentCodes(classfileBuffer);

            ClassWriter cw = new ClassWriter(0);

            if (debug) {
                ClassVisitor cv = cw;

                if (traceTransformedClass != null && className.equals(traceTransformedClass)) {
                    cv = getTraceClassVisitor(cv, className);
                }

                if (checkTransformedClass != null && className.equals(checkTransformedClass)) {
                    cv = new CheckClassAdapter(cv);
                }

                classNode.accept(cv);

                if (outputTransformedClass != null && className.equals(outputTransformedClass)) {
                    outputTransformedClass(cw.toByteArray(), className);
                }
            } else {
                classNode.accept(cw);
            }

            return cw.toByteArray();
        } catch (Exception e) {
            if (debug)
                e.printStackTrace();

            IllegalClassFormatException exception = new IllegalClassFormatException();
            exception.initCause(e);
            throw exception;
        }
    }

    private void outputTransformedClass(byte[] bytes, String className) throws IOException {
        String outputFile = className.replaceAll("/", "_") + ".class";
        OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile));
        out.write(bytes);
        out.flush();
    }

    private ClassVisitor getTraceClassVisitor(ClassVisitor cv, String className) throws FileNotFoundException {
        String traceFile = className.replaceAll("/", "_") + ".java";
        return new TraceClassVisitor(cv, new PrintWriter(traceFile));
    }

    public static void instrumentMethodCallStart(String callerClass,
                                                 String callerMethod,
                                                 String calleeClass,
                                                 String calleeMethod,
                                                 int opcode,
                                                 int line)
    {
        instrumenter.methodCallStart(callerClass, callerMethod, calleeClass, calleeMethod, opcode, line);
    }

    public static void instrumentMethodCallFinish() {
        instrumenter.methodCallFinish();
    }

    public static void instrumentThrowException() {
        instrumenter.methodThrowException();
    }

    public static void instrumetCatchException(MethodCallContext context) {
        instrumenter.methodCatchException(context);
    }

    public static MethodCallContext getOrCreateCurrentCallContext(String className, String methodName) {
        return instrumenter.getOrCreateCurrentCallContext(className, methodName);
    }

    private boolean isInstrumentJdkClass(String name) {
        return name.startsWith("java/") || name.startsWith("javax")
                || name.startsWith("jdk") || name.startsWith("sun")
                || name.startsWith("com/sun");
    }

    private ClassNode injectInstrumentCodes(byte[] classfileBuffer) {
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassNode classNode = new ClassNode(ASM7);

        cr.accept(classNode, ClassReader.EXPAND_FRAMES);

        for (MethodNode methodNode : classNode.methods) {
            injectInspector(methodNode, classNode.name);
        }

        return classNode;
    }

    private void injectInspector(MethodNode methodNode, String className) {
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

            if (opcode == ATHROW) {
                InsnList il = new InsnList();
                il.add(new MethodInsnNode(INVOKESTATIC, TRANSFORMER_CLASS,
                        "instrumentThrowException", "()V", false));
                insnList.insert(insnNode.getPrevious(), il);
                continue;
            }

            if (insnNode instanceof MethodInsnNode) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;

                if (!instrumentJdkMethod && isInstrumentJdkClass(methodInsnNode.owner))
                    continue;

                hasMethodInsn = true;

                InsnList il = instrumentMethodCallStartInsnList(
                        className, methodNode.name,
                        methodInsnNode.owner, methodInsnNode.name,
                        opcode, lineNumber);

                if ("<init>".equals(methodInsnNode.name) && newInsn != null) {
                    insertInsnListBefore(il, newInsn, insnList);
                    newInsn = null;
                } else {
                    insertInsnListBefore(il, insnNode, insnList);
                }

                il = instrumentMethodCallFinishInsnList();
                insnList.insert(insnNode, il);

                continue;
            }

            if (insnNode instanceof InvokeDynamicInsnNode) {
                hasMethodInsn = true;
                InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) insnNode;
                InsnList il = instrumentMethodCallStartInsnList(
                        className, methodNode.name,
                        "__lambda__", invokeDynamicInsnNode.name,
                        opcode, lineNumber);

                insertInsnListBefore(il, insnNode, insnList);

                il = instrumentMethodCallFinishInsnList();
                insnList.insert(insnNode, il);

                continue;
            }
        }

        if (hasMethodInsn)
            methodNode.maxStack += 6;

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
            il.add(new MethodInsnNode(INVOKESTATIC, TRANSFORMER_CLASS,
                    "instrumetCatchException", "(L" + CALL_CONTEXT_CLASS + ";)V", false));
            insnList.insert(insnNode, il);
        }

        if (hasExceptionHandler) {
            List<LocalVariableNode> variables = methodNode.localVariables;
            variables.add(new LocalVariableNode("$methodCallEntry",
                    "L" + CALL_CONTEXT_CLASS + ";",
                    null,
                    firstLabel,
                    lastLabel,
                    methodNode.maxLocals));

            InsnList il = new InsnList();
            il.add(new LdcInsnNode(className));
            il.add(new LdcInsnNode(methodNode.name));
            il.add(new MethodInsnNode(INVOKESTATIC, TRANSFORMER_CLASS,
                    "getOrCreateCurrentCallContext",
                    "(Ljava/lang/String;Ljava/lang/String;)L" + CALL_CONTEXT_CLASS + ";",
                    false));
            il.add(new VarInsnNode(ASTORE, methodNode.maxLocals));
            insnList.insert(il);

            methodNode.maxLocals++;
        }
    }

    private void insertInsnListBefore(InsnList il, AbstractInsnNode target, InsnList insnList) {
        AbstractInsnNode prevTarget = target.getPrevious();

        if (prevTarget == null) {
            insnList.insert(il);
        } else {
            insnList.insert(prevTarget, il);
        }
    }

    private InsnList instrumentMethodCallStartInsnList(String callerClass,
                                                       String callerMethod,
                                                       String calleeClass,
                                                       String calleeMethod,
                                                       int opcode,
                                                       int line)
    {
        InsnList il = new InsnList();
        il.add(new LdcInsnNode(callerClass));
        il.add(new LdcInsnNode(callerMethod));
        il.add(new LdcInsnNode(calleeClass));
        il.add(new LdcInsnNode(calleeMethod));
        il.add(new LdcInsnNode(opcode));
        il.add(new LdcInsnNode(Integer.valueOf(line)));
        il.add(new MethodInsnNode(
                INVOKESTATIC,
                TRANSFORMER_CLASS,
                "instrumentMethodCallStart",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II)V",
                false));

        return il;
    }

    private InsnList instrumentMethodCallFinishInsnList() {
        InsnList il = new InsnList();
        il.add(new MethodInsnNode(INVOKESTATIC, TRANSFORMER_CLASS,
                "instrumentMethodCallFinish", "()V", false));

        return il;
    }
}
