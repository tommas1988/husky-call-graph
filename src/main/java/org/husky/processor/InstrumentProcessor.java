package org.husky.processor;

import org.husky.MethodCallContext;

public interface InstrumentProcessor {
    String LOG_PROCESSOR = "log";

    void processCallStart(MethodCallContext context);
    void processCallFinish(MethodCallContext context);
    void processThrowException(MethodCallContext context);
    void processCatchException(MethodCallContext context);
}
