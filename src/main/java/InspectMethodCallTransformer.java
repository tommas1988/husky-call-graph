
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.GETSTATIC;

public class InspectMethodCallTransformer implements ClassFileTransformer {
    private static final ThreadLocal<Boolean> transforming = new ThreadLocal<Boolean>() {
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
        if (transforming.get())
            return null;

        transforming.set(Boolean.TRUE);

        ClassReader cr = new ClassReader(classfileBuffer);
        ClassNode classNode = new ClassNode(ASM7);

        cr.accept(classNode, ClassReader.SKIP_FRAMES);

        for (MethodNode methodNode : classNode.methods) {
            injectInspector(methodNode);
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(cw);

        transforming.set(Boolean.FALSE);

        return cw.toByteArray();
    }

    private void injectInspector(MethodNode methodNode) {
        if (hasExceptionHandler(methodNode)) {
            methodNode.maxLocals++;

            InsnList insnList = new InsnList();
            insnList.add(new FieldInsnNode(GETSTATIC, "MethodContextStack", "TOP", "LMethodContextStack$MethodContext;"));
            insnList.add(new VarInsnNode(ASTORE, methodNode.maxLocals));
        }
    }

    private boolean hasExceptionHandler(MethodNode methodNode) {
        return true;
    }
}
