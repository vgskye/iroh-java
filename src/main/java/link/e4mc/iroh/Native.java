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

public class Native {
    static {
        loadNativeLibrary();
    }

    private static void loadNativeLibrary() {
        String libName = "iroh_java";
        libName += '_' + PlatformDependent.normalizedOs()
                + '_' + PlatformDependent.normalizedArch()
                + "_278c6cb";

        String libraryPath = System.getProperty("link.e4mc.dialtone.native_path");
        boolean downloaded = false;

        while (libraryPath == null) {
            String home = System.getProperty("user.home");
            String fileName = System.mapLibraryName(libName);
            String folderPath = home + File.separatorChar + ".e4mc_cache";
            libraryPath = folderPath + File.separatorChar + fileName;
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
            case "iroh_java_windows_x86_64_278c6cb.dll":
                return new byte[]{-108, 126, 115, 123, -49, -23, 92, -77, 111, -83, -32, 40, 49, 92, 66, 37, -25, -119, -25, -77, -97, 23, -109, 11, -77, -16, 51, 116, 74, 83, 36, 78};
            case "libiroh_java_linux_aarch_64_278c6cb.so":
                return new byte[]{27, 25, 73, 43, 57, 115, -26, -102, -111, -71, 113, 81, -46, 17, -71, 5, 60, 108, 109, -105, -45, 64, 53, -45, -111, 32, -68, 53, -66, -74, 16, 10};
            case "libiroh_java_linux_x86_64_278c6cb.so":
                return new byte[]{-25, 81, -5, 61, 78, -30, 50, -38, 20, -9, -34, -89, 126, -30, 100, 27, -98, -87, 3, 101, -41, 107, 110, -52, 45, -85, -31, -110, -73, -94, 68, 66};
            case "libiroh_java_osx_aarch_64_278c6cb.dylib":
                return new byte[]{-41, -80, 87, -40, -94, -42, 51, 104, 93, 126, 68, -101, -16, -30, -110, -119, -114, -120, -122, -22, -111, -36, 121, 39, 22, 17, -106, 14, -70, -35, 11, -40};
            case "libiroh_java_osx_x86_64_278c6cb.dylib":
                return new byte[]{-77, 41, -74, -124, -105, 101, -125, 73, -28, -64, -12, -76, 9, 53, 114, 13, 80, -113, -88, -1, -22, -54, -21, 79, -125, 66, 121, -63, 22, -73, -68, -45};
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
