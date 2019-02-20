package org.husky;

public class MethodCallContextStack {
    public static class MethodCallContext {
        public final int id;
        public final int callerId;

        public String className;
        public String methodName;
        public int methodType;
        public int lineNumber;

        public MethodCallContext prev;
        public MethodCallContext next;

        public MethodCallContext() {
            /* TODO calculate id & callerId */
            id = 1;
            callerId = 0;
        }
    }

    private static final MethodCallContext HEAD = new MethodCallContext();

    public static MethodCallContext top = HEAD;

    public static MethodCallContext createAndPushContext(String owner, String name, int type, int line) {
        MethodCallContext context = new MethodCallContext();
        context.className = owner;
        context.methodName = name;
        context.methodType = type;
        context.lineNumber = line;

        push(context);

        return context;
    }

    public static void push(MethodCallContext context) {
        MethodCallContext currTop = peek();
        currTop.next = context;
        top = context;
    }

    public static MethodCallContext peek() {
        return top;
    }

    public static void resetTop(MethodCallContext context) {
        System.out.println("org.husky.MethodCallContextStack::resetTop invoked");
        context.next = null;
        top = context;
    }

    public static void pop() {
        top = top.prev;
        top.next = null;
    }
}
