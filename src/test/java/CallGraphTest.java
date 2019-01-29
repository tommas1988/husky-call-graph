public class CallGraphTest {
    public void foo() {
        bar();
    }

    public static void bar() {}

    public static void main(String[] args) {
        MethodCallRecorder.context.methodName = "test";
        /*CallGraphTest o = new CallGraphTest();
        o.foo();*/
    }
}
