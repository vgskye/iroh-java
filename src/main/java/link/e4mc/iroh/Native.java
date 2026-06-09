package link.e4mc.iroh;

import io.netty.util.internal.PlatformDependent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

public class Native {
    static {
        loadNativeLibrary();
    }

    private static void loadNativeLibrary() {
        String libName = "iroh_java";
        libName += '_' + PlatformDependent.normalizedOs()
                + '_' + PlatformDependent.normalizedArch()
                + "_0691f89";

        String libraryPath = System.getProperty("link.e4mc.dialtone.native_path");
        boolean downloaded = false;

        while (libraryPath == null) {
            String fileName;
            String folderPath;
            if (Objects.equals(PlatformDependent.normalizedOs(), "linux")) { // use XDG_CACHE_HOME on Linux, see https://github.com/vgskye/e4mc-minecraft-architectury/issues/243
                String xdgCacheHome = System.getenv("XDG_CACHE_HOME");
                if (xdgCacheHome == null) {
                    xdgCacheHome = Paths.get(System.getProperty("user.home"), ".cache").toString();
                }

                fileName = System.mapLibraryName(libName);
                folderPath = Paths.get(xdgCacheHome, "e4mc_cache").toString();
                libraryPath = Paths.get(folderPath, fileName).toString();
            } else {
                String home = System.getProperty("user.home");
                fileName = System.mapLibraryName(libName);
                folderPath = home + File.separatorChar + ".e4mc_cache";
                libraryPath = folderPath + File.separatorChar + fileName;
            }

            new File(folderPath).mkdirs();

            if (!new File(libraryPath).isFile()) {
                try {
                    URL url = new URL(System.getProperty("link.e4mc.dialtone.native_url", "https://natives.e4mc.link/" + fileName));
                    ReadableByteChannel rbc = Channels.newChannel(url.openStream());
                    FileOutputStream fos = new FileOutputStream(libraryPath);
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();
                    rbc.close();
                    downloaded = true;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                Path tempDir = Files.createTempDirectory("e4mc_temp");
                tempDir.toFile().deleteOnExit();

                FileInputStream fis = new FileInputStream(libraryPath);
                byte[] buf = new byte[fis.available()];
                fis.read(buf);
                fis.close();

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashed = digest.digest(buf);
                byte[] expected = get_native_hash(fileName);
                if (!Arrays.equals(hashed, expected)) {
                    // Our download attempt failed, just bail out
                    if (downloaded) {
                        throw new RuntimeException("Could not download a valid native!");
                    }
                    Files.delete(Paths.get(libraryPath));
                    libraryPath = null;
                    continue;
                }

                Path tempFile = tempDir.resolve(fileName);
                FileOutputStream fos = new FileOutputStream(tempFile.toString());
                fos.write(buf);
                fos.close();
                libraryPath = tempFile.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        try {
            System.load(libraryPath);
        } catch (UnsatisfiedLinkError e) {
            throw e;
        }
    }

    private static byte[] get_native_hash(String filename) {
        switch (filename) {
            case "iroh_java_windows_aarch_64_0691f89.dll":
                return new byte[]{-105, 2, 1, 1, 26, 49, 28, 15, -112, 123, -31, -38, 49, 81, 56, -128, -71, -18, 69, 27, 117, 29, -107, 40, 115, 91, 79, -92, -44, -21, 50, 108};
            case "iroh_java_windows_x86_64_0691f89.dll":
                return new byte[]{-97, -102, 6, 105, -124, -10, -40, -46, -84, -50, 95, 65, 92, -96, 42, -45, 36, 115, 102, -96, -96, 39, -40, 17, -101, 88, -119, 38, 64, -111, -34, -100};
            case "libiroh_java_linux_aarch_64_0691f89.so":
                return new byte[]{-100, 100, 5, -72, 80, 37, 50, 41, -118, -78, -5, 28, 7, -3, -28, -83, -87, -22, 55, 35, 92, 55, 98, -56, 54, -1, 110, 9, -25, 16, -78, -78};
            case "libiroh_java_linux_x86_64_0691f89.so":
                return new byte[]{-108, -16, 27, 65, 64, 12, 54, 117, 20, 48, 82, -108, -42, 17, 31, 51, 70, -76, 126, 77, 92, 85, 42, -31, -110, -15, -54, -32, 69, -112, -11, 103};
            case "libiroh_java_osx_aarch_64_0691f89.dylib":
                return new byte[]{54, 17, -79, -101, 39, -86, -65, -78, -1, -80, 40, 60, 27, 124, 0, -56, -115, 62, 106, 40, -49, 76, 97, 115, -57, -31, -20, -92, -16, -46, -105, 83};
            case "libiroh_java_osx_x86_64_0691f89.dylib":
                return new byte[]{34, 6, -6, -90, 124, 93, 57, 17, -90, -104, -122, -33, 3, 82, 67, -77, -65, 16, 100, -121, 65, -94, -36, -14, -35, -89, -33, 89, -64, -33, -84, 22};
        }
        return new byte[]{};
    }

    static native Object resolveDeferredInitializer(DeferredInitializer<?> deferredInitializer);
    static native void initEndpointBundle(Endpoint bundle, byte[][] alpns, String[] relays);
    static native void freeEndpointBundle(Endpoint bundle);
    static native Runnable pollEndpointBundle(Endpoint bundle);
    static native String addrEndpointBundle(Endpoint bundle);
    static native void watchAddrEndpointBundle(Endpoint bundle, Resolvable<String> future);
    static native void onlineEndpointBundle(Endpoint bundle, Resolvable<String> future);
    static native void closeEndpointBundle(Endpoint bundle, Resolvable<Void> future);
    static native void acceptEndpointBundle(Endpoint bundle, Resolvable<Void> preFuture, Resolvable<Connection> future);
    static native void connectEndpointBundle(Endpoint bundle, String addr, byte[] alpn, Resolvable<Connection> future);
    static native void closeIrohConnection(Connection connection, long code, byte[] reason);
    static native String addrIrohConnection(Connection connection);
    static native byte[] exportKeyingMaterialIrohConnection(Connection connection, byte[] label, byte[] context, int length);
    static native void acceptBiIrohConnection(Connection connection, Resolvable<Stream> future);
    static native void acceptUniIrohConnection(Connection connection, Resolvable<Stream> future);
    static native void openBiIrohConnection(Connection connection, Resolvable<Stream> future);
    static native void openUniIrohConnection(Connection connection, Resolvable<Stream> future);
    static native void readIrohStreamByteBuffer(Stream stream, ByteBuffer buffer, long offset, long maxlen, Resolvable<Long> future);
    static native void readIrohStreamByteArray(Stream stream, long maxlen, Resolvable<byte[]> future);
    static native void writeIrohStreamByteBuffer(Stream stream, ByteBuffer buffer, long offset, long len, Resolvable<Void> future);
    static native void writeIrohStreamByteArray(Stream stream, byte[] array, long offset, long len, Resolvable<Void> future);
    static native void freeIrohStream(Stream stream);
    static native String sanitizeTicket(String ticket);
}
