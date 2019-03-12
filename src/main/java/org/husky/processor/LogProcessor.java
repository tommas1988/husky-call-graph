package org.husky.processor;

import org.husky.MethodCallContext;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class LogProcessor implements InstrumentProcessor {
    private Map<Long, PrintWriter> writers = new HashMap<Long, PrintWriter>();

    public void processCallStart(MethodCallContext context) {
        write(content("+", context));
    }

    public void processCallFinish(MethodCallContext context) {
        write(content("-", context));
    }

    public void processThrowException(MethodCallContext context) {
        write(content("*", context));
    }

    public void processCatchException(MethodCallContext context) { }

    private String content(String prefix, MethodCallContext context) {
        MethodCallContext callerContext = context.prev;
        return prefix + " " + context.id + " " + callerContext.id +
                " " + context.className + "." + context.methodName + " " +
                callerContext.className + "." + callerContext.methodName +
                " " + context.line;
    }

    private void write(String content) {
        Thread thread = Thread.currentThread();
        Long tid = thread.getId();
        PrintWriter writer;
        if ((writer = writers.get(tid)) == null) {
            String fileName = thread.getName() + "_" + tid;
            try {
                writer = new PrintWriter(fileName);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("cannot create log file: " + fileName);
            }

            writers.put(tid, writer);
        }

        writer.println(content);
        writer.flush();
    }
}
