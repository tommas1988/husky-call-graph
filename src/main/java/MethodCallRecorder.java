public class MethodCallRecorder {
    public static void record(MethodContext context) {
        String out = context.lineNumber + "" + context.className + " "
                + context.methodName + " " + context.methodType;
        System.out.println(out);
    }
}
