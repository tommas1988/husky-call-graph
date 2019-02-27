package org.husky;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.WeakHashMap;

import static org.objectweb.asm.Opcodes.*;

public class MethodCallInstrumenter {
    public static final int INIT = 0;
    public static final int CINIT = 1;
    public static final int INSTANCE = 2;
    public static final int STATIC = 3;
    public static final int LAMBDA = 4;

    private static boolean instrumentJdkMethod = true;

    private static Object instrumentLock = new Object();

    private static boolean instrumenting = false;

    private static final MethodCallEntry ROOT_METHOD_CALL = new MethodCallEntry(0, null);

    private static WeakHashMap<Thread, MethodCallIdGenerator> idGenerator = new WeakHashMap<Thread, MethodCallIdGenerator>();
    private static WeakHashMap<Thread, MethodCallEntry> methodCallStack = new WeakHashMap<Thread, MethodCallEntry>();
    private static WeakHashMap<Thread, RandomAccessFile> recordFiles = new WeakHashMap<Thread, RandomAccessFile>();

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

    public static void methodCallStart(String owner, String name, int opcode, int line) throws IOException {
        if (instrumentJdkMethod) {
            if (!instrumenting) {
                synchronized (instrumentLock) {
                    if (!instrumenting)
                        recordMethodCall(owner, name, opcode, line);
                }
            }
        } else {
            recordMethodCall(owner, name, opcode, line);
        }
    }

    private static void recordMethodCall(String owner, String name, int opcode, int line) throws IOException {
        Thread thread = Thread.currentThread();
        MethodCallIdGenerator idGenerator;
        if ((idGenerator = MethodCallInstrumenter.idGenerator.get(thread)) == null) {
            idGenerator = new MethodCallIdGenerator();

            MethodCallInstrumenter.idGenerator.put(thread, idGenerator);
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
            methodType = CINIT;
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

    public static void methodCallFinish() {
        if (instrumentJdkMethod) {
            if (!instrumenting) {
                synchronized (instrumentLock) {
                    if (!instrumenting)
                        doMethodCallFinish();
                }
            }
        } else {
            doMethodCallFinish();
        }
    }

    private static void doMethodCallFinish() {
        Thread thread = Thread.currentThread();
        methodCallStack.put(thread, methodCallStack.get(thread).prev);
    }

    public static MethodCallEntry getCurrentMethodCallEntry() {
        if (instrumentJdkMethod) {
            if (!instrumenting) {
                synchronized (instrumentLock) {
                    if (!instrumenting)
                        return doGetCurrentMethodCallEntry();
                }
            }
        } else {
            return doGetCurrentMethodCallEntry();
        }
    }

    private static MethodCallEntry doGetCurrentMethodCallEntry() {
        Thread thread = Thread.currentThread();
        MethodCallEntry entry;
        if ((entry = methodCallStack.get(thread)) == null) {
            entry = ROOT_METHOD_CALL;
            methodCallStack.put(thread, entry);
        }
        return entry;
    }

    public static void resetMethodCallEntry(MethodCallEntry entry) {
        if (MethodCallInstrumenter.instrumentJdkMethod) {
            if (!instrumenting) {
                synchronized (instrumentLock) {
                    if (!instrumenting)
                        doResetresetMethodCallEntry(entry);
                }
            }
        } else {
            doResetresetMethodCallEntry(entry);
        }
    }

    private static void doResetresetMethodCallEntry(MethodCallEntry entry) {
        Thread thread = Thread.currentThread();
        methodCallStack.put(thread, entry);
    }
}
