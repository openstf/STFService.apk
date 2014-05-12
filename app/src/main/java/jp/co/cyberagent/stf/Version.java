package jp.co.cyberagent.stf;

public class Version {
    /**
     * Ideally we'd use the AndroidManifest value for this. However, we must be able to access
     * the same value from non-Context code as well, so to keep things easy, we'll just use
     * a plain string as our version number.
     */
    public static final String name = "0.7.2";
}
