public class CallGraphTest {
    public void foo() {
        try {
            bar();
        } catch (RuntimeException e) {
            System.out.println("RuntimeException");
        } catch (Exception e) {
            System.out.println("Exception");
        }
    }

    public static void bar() {
        try {
            System.out.println("I`m bar");
        } finally {
            System.out.println("finally");
        }

        throw new RuntimeException("Cannot go on");
    }

    public static void main(String[] args) {
        CallGraphTest o = new CallGraphTest();
        o.foo();
    }
}
