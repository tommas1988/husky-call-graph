import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import static org.objectweb.asm.Opcodes.ASM7;

public class CallGraphAgent {
    private static final ThreadLocal<Boolean> transforming = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {

            public byte[] transform(ClassLoader loader,
                    String className,
                    Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
                if (transforming.get())
                    return null;

                transforming.set(Boolean.TRUE);

                ClassReader cr = new ClassReader(classfileBuffer);
                ClassWriter cw = new ClassWriter(cr, 0);
                InspectMethodCallClassVisitor cv = new InspectMethodCallClassVisitor(ASM7, cw);

                cr.accept(cv, ClassReader.SKIP_FRAMES);

                transforming.set(Boolean.FALSE);

                return cw.toByteArray();
            }
        });
    }
}
