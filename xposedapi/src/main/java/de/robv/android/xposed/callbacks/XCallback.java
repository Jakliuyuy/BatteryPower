package de.robv.android.xposed.callbacks;

/**
 * Compile-time stub of the Xposed XCallback base class.
 * The real implementation is provided by LSPosed at runtime.
 */
public abstract class XCallback {

    public static abstract class Param {
        public Object[] args;
        public Object result;
        public Throwable throwable;
        public boolean returnEarly;

        protected Param() {
        }
    }
}
