import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import static org.objectweb.asm.Opcodes.*;

public class CallGraphTransformer implements ClassFileTransformer {
    private ThreadLocal<Boolean> injecting = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer)
        throws IllegalClassFormatException {
        if (injecting.get()) return null;

        injecting.set(Boolean.TRUE);

        ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(cr, 0);
        InspectMethodCallClassVisitor cv = new InspectMethodCallClassVisitor(ASM7, cw);

        cr.accept(cv, ClassReader.SKIP_FRAMES);

        injecting.set(Boolean.FALSE);

        return cw.toByteArray();
    }
}
