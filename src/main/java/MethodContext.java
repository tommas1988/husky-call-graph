public class MethodContext {
    public String className;
    public String methodName;
    public int methodType;
    public int lineNumber;

    public MethodContext prev;
    public MethodContext next;
}
