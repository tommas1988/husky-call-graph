
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Iterator;

import static org.objectweb.asm.Opcodes.*;

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

        ClassNode classNode = byte2ClassNode(classfileBuffer);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(cw);

        transforming.set(Boolean.FALSE);

        return cw.toByteArray();
    }

    private ClassNode byte2ClassNode(byte[] classfileBuffer) {
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassNode classNode = new ClassNode(ASM7);

        cr.accept(classNode, ClassReader.SKIP_FRAMES);

        for (MethodNode methodNode : classNode.methods) {
            injectInspector(methodNode, classNode.name);
        }

        return classNode;
    }

    private void injectInspector(MethodNode methodNode, String className) {
        InsnList insnList = methodNode.instructions;
        Iterator<AbstractInsnNode> iterator = insnList.iterator();
        int lineNumber = -1;
        while (iterator.hasNext()) {
            AbstractInsnNode insnNode = iterator.next();
            int opcode = insnNode.getOpcode();

            if (insnNode instanceof LineNumberNode) {
                lineNumber = ((LineNumberNode) insnNode).line;
                continue;
            }


            if (insnNode instanceof MethodInsnNode) {
                InsnList il = new InsnList();
                il.add(new MethodInsnNode(INVOKESTATIC, "MethodContextStack",
                        "newContext", "()LMethodContext;", false));

                il.add(new InsnNode(DUP));
                il.add(new LdcInsnNode(className));
                il.add(new FieldInsnNode(PUTFIELD, "MethodContext",
                        "className", "Ljava/lang/String;"));

                il.add(new InsnNode(DUP));
                il.add(new LdcInsnNode(methodNode.name));
                il.add(new FieldInsnNode(PUTFIELD, "MethodContext",
                        "methodName", "Ljava/lang/String;"));

                il.add(new InsnNode(DUP));
                il.add(new LdcInsnNode(opcode));
                il.add(new FieldInsnNode(PUTFIELD, "MethodContext",
                        "methodType", "I"));

                il.add(new InsnNode(DUP));
                il.add(new LdcInsnNode(Integer.valueOf(lineNumber)));
                il.add(new FieldInsnNode(PUTFIELD, "MethodContext",
                        "lineNumber", "I"));

                il.add(new MethodInsnNode(INVOKESTATIC, "MethodCallRecorder",
                        "record", "()V", false));

                insnList.insert(insnNode.getPrevious(), il);

                continue;
            }

            if (opcode >= IRETURN && opcode <= RETURN) {
                InsnList il = new InsnList();
                il.add(new MethodInsnNode(INVOKESTATIC, "MethodContextStack",
                        "pop", "()V", false));
                insnList.insert(insnNode.getPrevious(), il);
            }
        }

        if (methodNode.tryCatchBlocks.size() == 0)
            return;

        boolean hasExceptionHandler = false;
        for (TryCatchBlockNode tryCatchBlock : methodNode.tryCatchBlocks) {
            if (tryCatchBlock.type == null) continue;

            if (!hasExceptionHandler) {
                methodNode.maxLocals++;
                hasExceptionHandler = true;
            }

            AbstractInsnNode insnNode = tryCatchBlock.handler.getNext();
            while (insnNode instanceof LineNumberNode ||
                    insnNode instanceof FrameNode ||
                    insnNode instanceof LabelNode)
                insnNode = insnNode.getNext();

            InsnList il = new InsnList();
            il.add(new VarInsnNode(ALOAD, methodNode.maxLocals));
            il.add(new MethodInsnNode(INVOKESTATIC, "MethodContextStack",
                    "resetTop", "()V", false));
            insnList.insert(insnNode, il);
        }

        if (hasExceptionHandler) {
            InsnList il = new InsnList();
            il.add(new FieldInsnNode(GETSTATIC, "MethodContextStack",
                    "TOP", "LMethodContext;"));
            il.add(new VarInsnNode(ASTORE, methodNode.maxLocals));
            insnList.insert(il);
        }
    }

    public static void main(String[] args) throws IOException {
        String classFile = args[0];
        ClassReader cr = new ClassReader(new FileInputStream(classFile));
        ClassWriter cw = new ClassWriter(cr, 0);
        cr.accept(cw, 0);
        byte[] classfileBuffer = cw.toByteArray();

        InspectMethodCallTransformer transformer = new InspectMethodCallTransformer();
        TraceClassVisitor cv = new TraceClassVisitor(new PrintWriter(System.out));
        ClassNode classNode = transformer.byte2ClassNode(classfileBuffer);
        classNode.accept(cv);
    }
}
