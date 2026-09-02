package de.robv.android.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Compile-time stub of XposedHelpers. Method signatures intentionally mirror the
 * runtime API so that module code compiles without an external maven dependency.
 * Bodies throw because they must never be executed in a non-hooked process.
 */
public final class XposedHelpers {

    private XposedHelpers() {
    }

    private static RuntimeException unsupported() {
        return new UnsupportedOperationException("XposedHelpers stub: only available under LSPosed");
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        throw unsupported();
    }

    public static Method findMethodExact(Class<?> clazz, String methodName, Object... parameterTypes) {
        throw unsupported();
    }

    public static Method findMethodExactIfExists(Class<?> clazz, String methodName, Object... parameterTypes) {
        try {
            Method m = findMethodExact(clazz, methodName, parameterTypes);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Constructor<?> findConstructorExact(Class<?> clazz, Object... parameterTypes) {
        throw unsupported();
    }

    public static Constructor<?> findConstructorExactIfExists(Class<?> clazz, Object... parameterTypes) {
        try {
            return findConstructorExact(clazz, parameterTypes);
        } catch (Throwable t) {
            return null;
        }
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        throw unsupported();
    }

    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {
        throw unsupported();
    }

    public static XC_MethodHook.Unhook findAndHookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) {
        throw unsupported();
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        throw unsupported();
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        throw unsupported();
    }

    public static Object getObjectField(Object obj, String fieldName) {
        throw unsupported();
    }

    public static Object getSurroundingThis(Object obj) {
        throw unsupported();
    }

    public static int getIntField(Object obj, String fieldName) {
        throw unsupported();
    }

    public static long getLongField(Object obj, String fieldName) {
        throw unsupported();
    }

    public static boolean getBooleanField(Object obj, String fieldName) {
        throw unsupported();
    }

    public static float getFloatField(Object obj, String fieldName) {
        throw unsupported();
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        throw unsupported();
    }

    public static void setIntField(Object obj, String fieldName, int value) {
        throw unsupported();
    }

    public static void setBooleanField(Object obj, String fieldName, boolean value) {
        throw unsupported();
    }

    public static void setLongField(Object obj, String fieldName, long value) {
        throw unsupported();
    }

    /** Utility kept to mirror the runtime helper; useful for diagnostics. */
    public static String describeMember(Member member) {
        if (member == null) {
            return "null";
        }
        return Modifier.toString(member.getModifiers()) + " " + member.getDeclaringClass().getName() + "#" + member.getName();
    }

    public static Field findField(Class<?> clazz, String fieldName) {
        throw unsupported();
    }
}
