import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.*;

public class InspectMethodCallClassVisitor extends ClassVisitor {
    InspectMethodCallClassVisitor(final int api, final ClassVisitor classVisitor) {
        super(api, classVisitor);
    }

    private static class MethodCallInspector extends MethodVisitor {
        private static final String METHOD_CALL_RECORDER = "MethodCallRecorder";
        private static final String METHOD_CALL_CONTEXT = METHOD_CALL_RECORDER + "$MethodCallContext";
        private static final String RECORD_METHOD = "record";

        public MethodCallInspector(final int api, final MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }

        @Override
        public void visitMethodInsn(
                final int opcode,
                final String owner,
                final String name,
                final String descriptor,
                final boolean isInterface) {
            mv.visitFieldInsn(GETSTATIC, METHOD_CALL_RECORDER, "context", "L" + METHOD_CALL_CONTEXT + ";");
            mv.visitInsn(DUP);
            mv.visitLdcInsn(owner);
            mv.visitFieldInsn(PUTFIELD, METHOD_CALL_CONTEXT, "className", "Ljava/lang/String;");

            mv.visitInsn(DUP);
            System.out.println(owner + "::" + name);
            mv.visitLdcInsn(name);
            mv.visitFieldInsn(PUTFIELD, METHOD_CALL_CONTEXT, "methodName", "Ljava/lang/String;");

            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        @Override
        public void visitInvokeDynamicInsn(
                final String name,
                final String descriptor,
                final Handle bootstrapMethodHandle,
                final Object... bootstrapMethodArguments) {
            System.out.println("Lambda::" + name);
            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
        }
    }

    @Override
    public MethodVisitor visitMethod(
            final int access,
            final String name,
            final String descriptor,
            final String signature,
            final String[] exceptions) {
        MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
        if (mv != null) {
            return new MethodCallInspector(api, mv);
        }

        return null;
    }
}
