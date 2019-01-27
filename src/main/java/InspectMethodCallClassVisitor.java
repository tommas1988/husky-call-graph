import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

public class InspectMethodCallClassVisitor extends ClassVisitor {
    InspectMethodCallClassVisitor(final int api, final ClassVisitor classVisitor) {
        super(api, classVisitor);
    }
}
