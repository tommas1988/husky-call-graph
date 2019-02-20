package org.husky;

public class MethodCallRecorder {
    public static void record(MethodCallContextStack.MethodCallContext context) {
        String out = context.lineNumber + " " + context.className + " "
                + context.methodName + " " + context.methodType;
        System.out.println(out);
    }
}
