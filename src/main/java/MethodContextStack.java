public class MethodContextStack {
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
        System.out.println("MethodContextStack::resetTop invoked");
        context.next = null;
        top = context;
    }

    public static void pop() {
        top = top.prev;
        top.next = null;
    }
}
