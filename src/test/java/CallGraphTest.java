public class CallGraphTest {
    public void foo() {
        MethodContextStack.MethodContext context = MethodContextStack.top;

        try {
            MethodContextStack.MethodContext bar = MethodContextStack.newContext();
            bar.className = "CallGraphTest";
            bar.methodName = "bar";
            bar();
        } catch (Exception e) {
            MethodContextStack.resetTop(context);
            System.out.println(e.getStackTrace());
        }

        MethodContextStack.pop();
    }

    public static void bar() {
        MethodContextStack.MethodContext context = MethodContextStack.top;

        throw new RuntimeException("Cannot go on");

        /*MethodContextStack.pop();*/
    }

    public static void main(String[] args) {
        MethodContextStack.MethodContext context = MethodContextStack.top;

        CallGraphTest o = new CallGraphTest();

        MethodContextStack.MethodContext foo = MethodContextStack.newContext();
        foo.className = "CallGraphTest";
        foo.methodName = "foo";
        o.foo();

        MethodContextStack.pop();
    }
}
