package org.husky;

public class AgentOption {
    private static final String DEBUG = "debug";
    private static final String INCLUDE_JDK_METHOD = "includeJdkMethod";
    private static final String THREAD = "thread";
    private static final String CAPTURE_START = "captureStart";
    private static final String CAPTURE_END = "captureEnd";
    private static final String PROCESSOR = "processor";
    private static final String OUTPUT_TRANSFORMED_CLASS = "outputTransformedClass";
    private static final String CHECK_TRANSFORMED_CLASS = "checkTransformedClass";

    private boolean debug = false;
    private boolean includeJdkMethod = false;
    private long thread = 0;
    private String captureStart;
    private String captureEnd;
    private String processor;
    private String outputTransformedClass;
    private String checkTransformedClass;

    public AgentOption(String options) {
        if (options != null)
            parse(options);
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isIncludeJdkMethod() {
        return includeJdkMethod;
    }

    public long getThread() {
        return thread;
    }

    public String getCaptureStart() {
        return captureStart;
    }

    public String getCaptureEnd() {
        return captureEnd;
    }

    public String getProcessor() {
        return processor;
    }

    public String getOutputTransformedClass() {
        return outputTransformedClass;
    }

    public String getCheckTransformedClass() {
        return checkTransformedClass;
    }

    private void parse(String options) {
        if (options == null && options.length() == 0)
            return;

        String[] parts = options.split(":");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.equals(DEBUG)) {
                debug = true;
            } else if (part.equals(INCLUDE_JDK_METHOD)) {
                includeJdkMethod = true;
            } else if (part.startsWith(THREAD)) {
                thread = Long.valueOf(parseArgumentOption(part));
            } else if (part.startsWith(CAPTURE_START)) {
                captureStart = parseArgumentOption(part);
            } else if (part.startsWith(CAPTURE_END)) {
                captureEnd = parseArgumentOption(part);
            } else if (part.startsWith(PROCESSOR)) {
                processor = parseArgumentOption(part);
            } else if (part.startsWith(OUTPUT_TRANSFORMED_CLASS)) {
                outputTransformedClass = parseArgumentOption(part);
            } else if (part.startsWith(CHECK_TRANSFORMED_CLASS)) {
                checkTransformedClass = parseArgumentOption(part);
            }
        }
    }

    private static String parseArgumentOption(String option) {
        String parts[] = option.split("=");
        if (parts.length < 2)
            throw new IllegalArgumentException(option);

        return parts[1];
    }
}
