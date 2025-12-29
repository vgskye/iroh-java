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
import java.util.concurrent.CompletableFuture;

public class Native {
    static {
        loadNativeLibrary();
    }

    private static void loadNativeLibrary() {
        String libName = "iroh_java";
        libName += '_' + PlatformDependent.normalizedOs()
                + '_' + PlatformDependent.normalizedArch()
                + "_6b275ec";

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
            case "iroh_java_windows_x86_64_6b275ec.dll":
                return new byte[]{-116, 30, 103, 89, 70, 112, 91, -6, -95, -14, -96, -20, -22, 63, -76, 6, -121, 70, -74, -65, 26, 99, 58, -4, 98, 123, -74, -103, -78, -14, -22, 47};
            case "libiroh_java_linux_x86_64_6b275ec.so":
                return new byte[]{15, 82, 110, 123, 60, 78, -68, 42, -97, -83, -24, 126, 69, 111, 7, -89, 61, -40, -109, 101, 98, 53, 17, 40, 113, 34, 116, 50, 11, 105, 45, -12};
            case "libiroh_java_osx_aarch_64_6b275ec.dylib":
                return new byte[]{-99, -91, -38, 125, -72, 112, 48, -2, 24, -105, 70, -108, -24, 29, 62, -126, -14, -71, -75, 4, -38, 26, 69, -48, -56, 92, 55, -65, -64, -25, 106, -28};
            case "libiroh_java_osx_x86_64_6b275ec.dylib":
                return new byte[]{62, 81, -68, -64, -95, -41, 23, 20, -50, -61, 114, -118, -127, 96, -112, 108, -58, -25, -121, -89, -37, 16, 34, 101, -62, -105, -67, -68, -26, -21, 117, 13};
        }
        return new byte[]{};
    }

    static native Object resolveDeferredInitializer(DeferredInitializer<?> deferredInitializer);
    static native void initEndpointBundle(Endpoint bundle, byte[][] alpns);
    static native void freeEndpointBundle(Endpoint bundle);
    static native Runnable pollEndpointBundle(Endpoint bundle);
    static native String addrEndpointBundle(Endpoint bundle);
    static native void onlineEndpointBundle(Endpoint bundle, CompletableFuture<String> future);
    static native void closeEndpointBundle(Endpoint bundle, CompletableFuture<Void> future);
    static native void acceptEndpointBundle(Endpoint bundle, CompletableFuture<Void> preFuture, CompletableFuture<Connection> future);
    static native void connectEndpointBundle(Endpoint bundle, String addr, byte[] alpn, CompletableFuture<Connection> future);
    static native void closeIrohConnection(Connection connection, long code, byte[] reason);
    static native String addrIrohConnection(Connection connection);
    static native byte[] exportKeyingMaterialIrohConnection(Connection connection, byte[] label, byte[] context, int length);
    static native void acceptBiIrohConnection(Connection connection, CompletableFuture<Stream> future);
    static native void acceptUniIrohConnection(Connection connection, CompletableFuture<Stream> future);
    static native void openBiIrohConnection(Connection connection, CompletableFuture<Stream> future);
    static native void openUniIrohConnection(Connection connection, CompletableFuture<Stream> future);
    static native void readIrohStreamByteBuffer(Stream stream, ByteBuffer buffer, long offset, long maxlen, CompletableFuture<Long> future);
    static native void readIrohStreamByteArray(Stream stream, long maxlen, CompletableFuture<byte[]> future);
    static native void writeIrohStreamByteBuffer(Stream stream, ByteBuffer buffer, long offset, long len, CompletableFuture<Void> future);
    static native void writeIrohStreamByteArray(Stream stream, byte[] array, long offset, long len, CompletableFuture<Void> future);
    static native void freeIrohStream(Stream stream);
}
