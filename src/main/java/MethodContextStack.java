public class MethodContextStack {
    public static class MethodContext {
        public String className;
        public String methodName;
        public int methodType;
        public int lineNumber;

        public MethodContext prev;
        public MethodContext next;
    }

    private static final MethodContext HEAD = new MethodContext();

    public static MethodContext top = HEAD;

    public static MethodContext newContext() {
        MethodContext context = new MethodContext();
        context.prev = top;
        top.next = context;
        top = context;

        return context;
    }

    public static void resetTop(MethodContext context) {
        context.next = null;
        top = context;
    }

    public static void pop() {
        top = top.prev;
        top.next = null;
    }
}
