package Data;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Only allows the classes the saved queue data actually contains, blocking code-execution payloads. */
public class SafeObjectInputStream extends ObjectInputStream {
    private static final Set<String> ALLOWED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "Store.Store",
            "Patient.Patient",
            "java.util.ArrayList",
            "java.lang.Integer",
            "java.lang.String",
            "[Ljava.lang.Object;",
            "[Ljava.lang.String;")));

    public SafeObjectInputStream(InputStream in) throws IOException { super(in); }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        if (!ALLOWED.contains(desc.getName()))
            throw new InvalidClassException("Refusing to load unexpected class: " + desc.getName());
        return super.resolveClass(desc);
    }
}
