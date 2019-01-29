import java.util.HashMap;
import java.util.Map;

public class MethodCallRecorder {
    public enum METHOD_TYPE {
        CONSTRUCTOR,
        STATIC_INIT,
        INSTANCE_METHOD,
        STATIC_METHOD,
        LAMBDA,
    }

    public static class MethodCallContext {
        public String className;
        public String methodName;
        public METHOD_TYPE methodType;
        public int lineNumber;
    }

    public static MethodCallContext context = new MethodCallContext();

    private static final Map<METHOD_TYPE, String> methodTypeMapper = new HashMap<METHOD_TYPE, String>();

    static {
        methodTypeMapper.put(METHOD_TYPE.CONSTRUCTOR, "constructor");
        methodTypeMapper.put(METHOD_TYPE.STATIC_INIT, "static initializer");
        methodTypeMapper.put(METHOD_TYPE.INSTANCE_METHOD, "instance method");
        methodTypeMapper.put(METHOD_TYPE.STATIC_METHOD, "static method");
        methodTypeMapper.put(METHOD_TYPE.LAMBDA, "lambda");
    }

    public static void record() {
        String out = context.className + " " + context.methodName;

        if (context.methodType != null)
            out += " " + methodTypeMapper.get(context.methodType);

        System.out.println(out);
    }
}
