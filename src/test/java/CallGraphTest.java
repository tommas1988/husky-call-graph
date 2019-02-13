public class CallGraphTest {
    private String arg;
    public CallGraphTest(String arg) {
        this.arg = arg;
    }

    public void foo() {
        bar();
        /*int i = intFunc();
        System.out.println(i);*/

        /*try {
            bar();
        } catch (RuntimeException e) {
            System.out.println("RuntimeException");
        } catch (Exception e) {
            System.out.println("Exception");
        }*/
    }

    public static void bar() {
        try {
            System.out.println("I`m bar");
        } finally {
            System.out.println("finally");
        }

        /*throw new RuntimeException("Cannot go on");*/
    }

    public static int intFunc() {
        return 1;
    }

    public static void main(String[] args) {
        CallGraphTest o = new CallGraphTest("arg");
        o.foo();

        /*SubClass.staticMethod(0);
        SubClass sub = new SubClass();
        sub.instanceMethod("instance method");*/
    }
}
