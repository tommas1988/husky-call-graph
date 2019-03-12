package org.husky.processor;

import org.husky.MethodCallContext;

public class NullProcessor implements InstrumentProcessor {
    public void processCallStart(MethodCallContext context) {}

    public void processCallFinish(MethodCallContext context) {}

    public void processThrowException(MethodCallContext context) {}

    public void processCatchException(MethodCallContext context) {}
}
