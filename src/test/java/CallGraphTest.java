public class CallGraphTest {
    public void foo() {
        bar();
    }

    public static void bar() {}

    public static void main(String[] args) {
        CallGraphTest o = new CallGraphTest();
        o.foo();
    }
}
