package org.husky;

import java.util.HashMap;

public class MethodCallContext {
    public final int id;

    public final String className;
    public final String methodName;
    public final int methodType;
    public final int line;
    public final int depth;

    public MethodCallContext prev;

    private static HashMap<Long, IdGenerator> idGenerators;

    static {
        idGenerators = new HashMap<Long, IdGenerator>();
    }

    private static class IdGenerator {
        private int id = 0;

        public int nextId() {
            return ++id;
        }
    }

    public MethodCallContext(MethodCallContext callerContext,
                             String className,
                             String methodName,
                             int methodType,
                             int line) {
        Long tid = Thread.currentThread().getId();
        if (callerContext == null) {
            id = 0;
            depth = 0;
            idGenerators.put(tid, new IdGenerator());
        } else {
            id = idGenerators.get(tid).nextId();
            depth = callerContext.depth + 1;
        }

        prev = callerContext;

        this.className = className;
        this.methodName = methodName;
        this.methodType = methodType;
        this.line = line;
    }
}
