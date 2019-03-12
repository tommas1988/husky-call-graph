package org.husky;

import org.husky.processor.InstrumentProcessor;
import org.husky.processor.LogProcessor;
import org.husky.processor.NullProcessor;

import java.util.HashMap;

import static org.objectweb.asm.Opcodes.*;

public class MethodCallInstrumenter {
    public static final int ROOT_METHOD = -1;
    public static final int INIT_METHOD = 0;
    public static final int CLASS_INIT_METHOD = 1;
    public static final int INSTANCE_METHOD = 2;
    public static final int STATIC_METHOD = 3;
    public static final int LAMBDA_METHOD = 4;

    private HashMap<Long, MethodCallContext> contextStacks = new HashMap<Long, MethodCallContext>();

    private boolean instrumentJdkMethod = false;
    private long instrumentThread = 0;
    private String instrumentStartPoint;
    private String instrumentEndPoint;
    private InstrumentProcessor processor;

    private static boolean instrumenting = false;

    public MethodCallInstrumenter() {
        processor = new NullProcessor();
    }

    public void setInstrumentJdkMethod(boolean instrumentJdkMethod) {
        this.instrumentJdkMethod = instrumentJdkMethod;
    }

    public void setInstrumentThread(long instrumentThread) {
        this.instrumentThread = instrumentThread;
    }

    public void setInstrumentStartPoint(String instrumentStartPoint) {
        this.instrumentStartPoint = instrumentStartPoint;
    }

    public void setInstrumentEndPoint(String instrumentEndPoint) {
        this.instrumentEndPoint = instrumentEndPoint;
    }

    public void setProcessor(String processorName) {
        if (processorName.equals(InstrumentProcessor.LOG_PROCESSOR)) {
            processor = new LogProcessor();
            return;
        }

        throw new IllegalArgumentException(processorName);
    }

    public void methodCallStart(String callerClass,
                                String callerMethod,
                                String calleeClass,
                                String calleeMethod,
                                int opcode,
                                int line)
    {
        if (instrumentJdkMethod) {
            synchronized (this) {
                if (instrumenting)
                    return;

                instrumenting = true;
                doMethodCallStart(callerClass, callerMethod, calleeClass, calleeMethod, opcode, line);
                instrumenting = false;
            }
        } else {
            doMethodCallStart(callerClass, callerMethod, calleeClass, calleeMethod, opcode, line);
        }
    }

    public void methodCallFinish() {
        if (instrumentJdkMethod) {
            synchronized (this) {
                if (instrumenting)
                    return;

                instrumenting = true;
                doMethodCallFinish();
                instrumenting = false;
            }
        } else {
            doMethodCallFinish();
        }
    }

    public void methodThrowException() {
        if (instrumentJdkMethod) {
            synchronized (this) {
                if (instrumenting)
                    return;

                instrumenting = true;
                doMethodThrowException();
                instrumenting = false;
            }
        } else {
            doMethodThrowException();
        }
    }

    public void methodCatchException(MethodCallContext context) {
        if (instrumentJdkMethod) {
            synchronized (this) {
                if (instrumenting)
                    return;

                instrumenting = true;
                doMethodCatchException(context);
                instrumenting = false;
            }
        } else {
            doMethodCatchException(context);
        }
    }

    public MethodCallContext getOrCreateCurrentCallContext(String className, String methodName) {
        if (instrumentJdkMethod) {
            synchronized (this) {
                if (instrumenting)
                    return null;

                instrumenting = true;
                MethodCallContext context = contextStackTop(className, methodName);
                instrumenting = false;

                return context;
            }
        } else {
            return contextStackTop(className, methodName);
        }
    }

    private boolean shouldInstrument(Long tid) {
        return instrumentThread == 0 || tid == instrumentThread;
    }

    private void doMethodCallStart(String callerClass,
                                   String callerMethod,
                                   String calleeClass,
                                   String calleeMethod,
                                   int opcode,
                                   int line)
    {
        Thread thread = Thread.currentThread();
        Long tid = thread.getId();

        MethodCallContext callerContext, calleeContext;
        callerContext = contextStackTop(tid, callerClass, callerMethod);

        int methodType;
        if ("<init>".equals(calleeMethod)) {
            methodType = INIT_METHOD;
        } else if ("<cinit>".equals(calleeMethod)) {
            methodType = CLASS_INIT_METHOD;
        } else {
            switch (opcode) {
                case INVOKEVIRTUAL:
                    methodType = INSTANCE_METHOD;
                    break;
                case INVOKESTATIC:
                    methodType = STATIC_METHOD;
                    break;
                case INVOKEDYNAMIC:
                    methodType = LAMBDA_METHOD;
                    break;
                default:
                    methodType = opcode;
            }
        }

        calleeContext = new MethodCallContext(callerContext, calleeClass, calleeMethod, methodType, line);
        contextStacks.put(tid, calleeContext);

        if (shouldInstrument(tid))
            processor.processCallStart(calleeContext);
    }

    private void doMethodCallFinish() {
        Long tid = Thread.currentThread().getId();
        MethodCallContext context = contextStacks.get(tid);
        contextStacks.put(tid, context.prev);

        if (shouldInstrument(tid))
            processor.processCallFinish(context);
    }

    private void doMethodThrowException() {
        Long tid = Thread.currentThread().getId();
        MethodCallContext context = contextStacks.get(tid);

        if (shouldInstrument(tid))
            processor.processThrowException(context);
    }

    private void doMethodCatchException(MethodCallContext context) {
        Long tid = Thread.currentThread().getId();
        contextStacks.put(tid, context);

        if (shouldInstrument(tid))
            processor.processCatchException(context);
    }

    private MethodCallContext contextStackTop(String className, String methodName) {
        return contextStackTop(Thread.currentThread().getId(), className, methodName);
    }

    private MethodCallContext contextStackTop(Long tid, String className, String methodName) {
        MethodCallContext context;
        if ((context = contextStacks.get(tid)) == null) {
            context = new MethodCallContext(null, className, methodName, ROOT_METHOD, 0);
            contextStacks.put(tid, context);
        }

        return context;
    }
}
