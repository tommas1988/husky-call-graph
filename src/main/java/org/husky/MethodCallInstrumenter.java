package org.husky;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;

import static org.objectweb.asm.Opcodes.*;

public class MethodCallInstrumenter {
    public static final int INIT = 0;
    public static final int CLASS_INIT = 1;
    public static final int INSTANCE = 2;
    public static final int STATIC = 3;
    public static final int LAMBDA = 4;

    private static final String INSTRUMENTER_NAME = "org/husky/MethodCallInstrumenter";
    private static final String METHOD_CALL_ENTRY_NAME = INSTRUMENTER_NAME + "$MethodCallEntry";

    private static MethodCallInstrumenter instance;

    private static boolean instrumenting = false;

    private static final MethodCallEntry ROOT_METHOD_CALL = new MethodCallEntry(0, null);

    private WeakHashMap<Thread, MethodCallIdGenerator> idGenerator;
    private WeakHashMap<Thread, MethodCallEntry> methodCallStack;
    private WeakHashMap<Thread, RandomAccessFile> recordFiles;

    private static class MethodCallIdGenerator {
        private int id = 0;

        public int getId() {
            return ++id;
        }
    }

    public static class MethodCallEntry {
        public final int id;
        public final MethodCallEntry prev;

        public MethodCallEntry(int id, MethodCallEntry prev) {
               this.id = id;
               this.prev = prev;
        }
    }

    private boolean instrumentJdkMethod;

    public MethodCallInstrumenter(boolean instrumentJdkMethod) {
        this.instrumentJdkMethod = instrumentJdkMethod;

        idGenerator = new WeakHashMap<Thread, MethodCallIdGenerator>();
        methodCallStack = new WeakHashMap<Thread, MethodCallEntry>();
        recordFiles = new WeakHashMap<Thread, RandomAccessFile>();

        instance = this;
    }

    public static void instrumentMethodCallStart(String owner, String name, int opcode, int line) throws IOException {
        instance.methodCallStart(owner, name, opcode, line);
    }

    public static void instrumentMethodCallFinish() {
        instance.methodCallFinish();
    }

    public static MethodCallEntry getCurrentMethodCallEntry() {
        return instance.getTopMethodCallEntry();
    }

    public static void setCurrentMethodCallEntry(MethodCallEntry entry) {
        instance.setTopMethodCallEntry(entry);
    }

    public void methodCallStart(String owner, String name, int opcode, int line) throws IOException {
        if (instrumentJdkMethod) {
            synchronized (this) {
                if (instrumenting)
                    return;

                instrumenting = true;
                doMethodCallStart(owner, name, opcode, line);
                instrumenting = false;
            }
        } else {
            doMethodCallStart(owner, name, opcode, line);
        }
    }

    public void methodCallFinish() {
        if (instrumentJdkMethod) {
            synchronized (this) {
                if (instrumenting)
                    return;

                instrumenting = true;
                doMethodCallFinish();
                instrumenting = false;
            }
        } else {
            doMethodCallFinish();
        }
    }

    public MethodCallEntry getTopMethodCallEntry() {
        if (instrumentJdkMethod) {
            synchronized (this) {
                if (instrumenting)
                    return null;

                instrumenting = true;
                MethodCallEntry entry = doGetCurrentMethodCallEntry();
                instrumenting = false;

                return entry;
            }
        } else {
            return doGetCurrentMethodCallEntry();
        }
    }

    public void setTopMethodCallEntry(MethodCallEntry entry) {
        if (instrumentJdkMethod) {
            synchronized (this) {
                if (instrumenting)
                    return;

                instrumenting = true;
                doSetTopMethodCallEntry(entry);
                instrumenting = false;
            }
        } else {
            doSetTopMethodCallEntry(entry);
        }
    }

    private void doMethodCallStart(String owner, String name, int opcode, int line) throws IOException {
        Thread thread = Thread.currentThread();
        MethodCallIdGenerator idGenerator;
        if ((idGenerator = this.idGenerator.get(thread)) == null) {
            idGenerator = new MethodCallIdGenerator();

            this.idGenerator.put(thread, idGenerator);
            methodCallStack.put(thread, ROOT_METHOD_CALL);
            recordFiles.put(thread, new RandomAccessFile("thread_" + thread.getName(), "rw"));
        }

        MethodCallEntry calleeEntry, callerEntry;
        int calleeId = idGenerator.getId();

        callerEntry = methodCallStack.get(thread);
        calleeEntry = new MethodCallEntry(calleeId, callerEntry);
        methodCallStack.put(thread, calleeEntry);

        int methodType;
        if ("<init>".equals(name)) {
            methodType = INIT;
        } else if ("<cinit>".equals(name)) {
            methodType = CLASS_INIT;
        } else {
            switch (opcode) {
                case INVOKEVIRTUAL:
                    methodType = INSTANCE;
                    break;
                case INVOKESTATIC:
                    methodType = STATIC;
                    break;
                case INVOKEDYNAMIC:
                    methodType = LAMBDA;
                    break;
                default:
                    methodType = opcode;
            }
        }

        String content = calleeId + " " + callerEntry.id + " " + owner + " " + name + " " + methodType + "\n";

        RandomAccessFile file = recordFiles.get(thread);
        file.writeChars(content);
    }

    private void doMethodCallFinish() {
        Thread thread = Thread.currentThread();
        methodCallStack.put(thread, methodCallStack.get(thread).prev);
    }

    private MethodCallEntry doGetCurrentMethodCallEntry() {
        Thread thread = Thread.currentThread();
        MethodCallEntry entry;
        if ((entry = methodCallStack.get(thread)) == null) {
            entry = ROOT_METHOD_CALL;
            methodCallStack.put(thread, entry);
        }
        return entry;
    }

    private void doSetTopMethodCallEntry(MethodCallEntry entry) {
        Thread thread = Thread.currentThread();
        methodCallStack.put(thread, entry);
    }

    public ClassNode injectInstrumentCodes(byte[] classfileBuffer) {
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

                InsnList il = instrumentMethodCallStartInsnList(methodInsnNode.owner, methodInsnNode.name, opcode, lineNumber);

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

                il = instrumentMethodCallFinishInsnList();
                insnList.insert(insnNode, il);

                continue;
            }

            if (insnNode instanceof InvokeDynamicInsnNode) {
                hasMethodInsn = true;
                InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) insnNode;
                InsnList il = instrumentMethodCallStartInsnList("", invokeDynamicInsnNode.name, opcode, lineNumber);
                if (insnNode.getPrevious() == null) {
                    insnList.insert(il);
                } else {
                    insnList.insert(insnNode.getPrevious(), il);
                }

                il = instrumentMethodCallFinishInsnList();
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
            il.add(new MethodInsnNode(INVOKESTATIC, INSTRUMENTER_NAME,
                    "setCurrentMethodCallEntry", "(L" + METHOD_CALL_ENTRY_NAME + ";)V", false));
            insnList.insert(insnNode, il);
        }

        if (hasExceptionHandler) {
            List<LocalVariableNode> variables = methodNode.localVariables;
            variables.add(new LocalVariableNode("$methodCallEntry",
                    "L" + METHOD_CALL_ENTRY_NAME + ";",
                    null,
                    firstLabel,
                    lastLabel,
                    methodNode.maxLocals));

            InsnList il = new InsnList();
            il.add(new MethodInsnNode(INVOKESTATIC, INSTRUMENTER_NAME,
                    "getCurrentMethodCallEntry", "()L" + METHOD_CALL_ENTRY_NAME + ";", false));
            il.add(new VarInsnNode(ASTORE, methodNode.maxLocals));
            insnList.insert(il);

            methodNode.maxLocals++;
        }
    }

    private InsnList instrumentMethodCallStartInsnList(String owner, String name, int opcode, int line) {
        InsnList il = new InsnList();
        il.add(new LdcInsnNode(owner));
        il.add(new LdcInsnNode(name));
        il.add(new LdcInsnNode(opcode));
        il.add(new LdcInsnNode(Integer.valueOf(line)));
        il.add(new MethodInsnNode(
                INVOKESTATIC,
                INSTRUMENTER_NAME,
                "instrumentMethodCallStart",
                "(Ljava/lang/String;Ljava/lang/String;II)V",
                false));

        return il;
    }

    private InsnList instrumentMethodCallFinishInsnList() {
        InsnList il = new InsnList();
        il.add(new MethodInsnNode(INVOKESTATIC, INSTRUMENTER_NAME,
                "instrumentMethodCallFinish", "()V", false));

        return il;
    }
}
