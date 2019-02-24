package org.husky;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.WeakHashMap;

import static org.objectweb.asm.Opcodes.*;

public class MethodCallContext {
    public static final int INIT = 0;
    public static final int CINIT = 1;
    public static final int INSTANCE = 2;
    public static final int STATIC =3;
    public static final int LAMBDA = 4;

    private final int id;
    private final int callerId;

    private String owner;
    private String name;
    private int type;
    private int line;

    private MethodCallContext prev;

    private static final MethodCallContext HEAD = new MethodCallContext(0, 0);

    private static WeakHashMap<Thread, ContextIdGenerator> contextIds = new WeakHashMap<Thread, ContextIdGenerator>();

    private static WeakHashMap<Thread, MethodCallContext> contextStacks = new WeakHashMap<Thread, MethodCallContext>();

    private static WeakHashMap<Thread, RandomAccessFile> recordFiles = new WeakHashMap<Thread, RandomAccessFile>();

    private static class ContextIdGenerator {
        private int id = 0;

        public int increment() {
            return ++id;
        }
    }

    private MethodCallContext(int id, int callerId) {
        this.id = id;
        this.callerId = callerId;
    }

    public static MethodCallContext createContext(String owner, String name, int type, int line) {
        Thread thread = Thread.currentThread();
        MethodCallContext topContext;
        if ((topContext = contextStacks.get(thread)) == null) {
            contextIds.put(thread, new ContextIdGenerator());
            topContext = HEAD;
        }

        int contextId = contextIds.get(thread).increment();
        MethodCallContext context = new MethodCallContext(contextId, topContext.id);
        context.owner = owner;
        context.name = name;
        context.type = type;
        context.line = line;

        context.prev = topContext;
        contextStacks.put(thread, context);

        return context;
    }

    public static void destroyCurrentContext() {
        Thread thread = Thread.currentThread();
        MethodCallContext top = contextStacks.get(thread);
        contextStacks.put(thread, top.prev);
    }

    public static MethodCallContext getCurrentContext() {
        return contextStacks.get(Thread.currentThread());
    }

    public static void setCurrentContext(MethodCallContext context) {
        contextStacks.put(Thread.currentThread(), context);
    }

    public static int getMethodType(int opcode, String name) {
        if ("<init>".equals(name)) {
            return INIT;
        } else if ("<cinit>".equals(name)) {
            return CINIT;
        } else {
            switch (opcode) {
                case INVOKEVIRTUAL:
                    return INSTANCE;
                case INVOKESTATIC:
                    return STATIC;
                case INVOKEDYNAMIC:
                    return LAMBDA;
                default:
                    return opcode;
            }
        }
    }

    public void record() throws IOException {
        Thread thread = Thread.currentThread();
        RandomAccessFile file;
        if ((file = recordFiles.get(thread)) == null) {
            file = new RandomAccessFile("thread_" + thread.getName(), "rw");
            recordFiles.put(thread, file);
        }

        String content = id + " " + callerId + " " + owner + " " + name + " " + type + "\n";
        file.writeChars(content);
    }
}
