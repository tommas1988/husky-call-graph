package org.husky;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

public class MonitorMethodCallTransformer implements ClassFileTransformer {
    private boolean instrumentJdkMethod = true;
    private MethodCallInstrumenter instrumenter;

    public MonitorMethodCallTransformer() {
        instrumenter = new MethodCallInstrumenter(instrumentJdkMethod);
    }

    public byte[] transform(ClassLoader loader,
                     String className,
                     Class<?> classBeingRedefined,
                     ProtectionDomain protectionDomain,
                     byte[] classfileBuffer)
            throws IllegalClassFormatException {
        try {

            if (!instrumentJdkMethod) {
                if (className.startsWith("java/") ||
                        className.startsWith("javax") ||
                        className.startsWith("jdk") ||
                        className.startsWith("sun") ||
                        className.startsWith("com/sun") ||
                        className.startsWith("org/husky/"))
                    return null;
            }

            ClassNode classNode = instrumenter.injectInstrumentCodes(classfileBuffer);

            ClassWriter cw = new ClassWriter(0);
            classNode.accept(cw);

            return cw.toByteArray();
        } catch (Exception e) {
            IllegalClassFormatException exception = new IllegalClassFormatException();
            exception.initCause(e);
            throw exception;
        }
    }

    public static void main(String[] args) throws IOException, NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {
        String classFile;
        boolean debug = false;
        if ("-debug".equals(args[0])) {
            classFile = args[1];
            debug = true;
        } else {
            classFile = args[0];
        }

        ClassReader cr = new ClassReader(new FileInputStream(classFile));
        ClassWriter cw = new ClassWriter(cr, 0);
        cr.accept(cw, ClassReader.SKIP_FRAMES);
        byte[] classfileBuffer = cw.toByteArray();

        MethodCallInstrumenter instrumenter = new MethodCallInstrumenter(true);

        if (debug) {
            ClassWriter classWriter = new ClassWriter(/*ClassWriter.COMPUTE_FRAMES | */ 0);
            ClassVisitor cv = new CheckClassAdapter(classWriter);

            ClassNode classNode = instrumenter.injectInstrumentCodes(classfileBuffer);
            classNode.accept(cv);

            classfileBuffer = classWriter.toByteArray();
            class TransformerClassLoader extends ClassLoader {
                public Class defineClass(String name, byte[] classfileBuffer) {
                    return defineClass(name, classfileBuffer, 0, classfileBuffer.length);
                }
            }
            Class clazz = (new TransformerClassLoader()).defineClass("CallGraphTest", classfileBuffer);
            Method method = clazz.getMethod("main", String[].class);
            method.invoke(null, (Object) args);
        } else {
            ClassVisitor cv = new TraceClassVisitor(cw, new PrintWriter(System.out));

            ClassNode classNode = instrumenter.injectInstrumentCodes(classfileBuffer);
            classNode.accept(cv);
        }
    }
}
