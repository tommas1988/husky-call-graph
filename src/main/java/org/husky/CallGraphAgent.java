package org.husky;

import java.lang.instrument.Instrumentation;

public class CallGraphAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new MonitorMethodCallTransformer());
    }
}
