package org.husky;

import java.io.RandomAccessFile;
import java.util.WeakHashMap;

public class MethodCallContext {
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
        MethodCallContext context = new MethodCallContext(contextId, topContext.callerId);
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
        return 0;
    }

    public void record() {

    }
}
