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
                + "_1f12e83";

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
            case "iroh_java_windows_x86_64_1f12e83.dll":
                return new byte[]{-34, 17, 66, 31, 118, 21, 84, 74, -124, 69, 40, -103, 41, -18, 27, 56, -73, -61, 35, 83, -2, 3, -59, 74, 61, -45, 72, -116, 43, -105, -93, 117};
            case "libiroh_java_linux_x86_64_1f12e83.so":
                return new byte[]{-103, -103, -97, 109, 33, -97, -114, -96, -51, -41, -128, 35, -16, 117, -66, 0, -2, -14, 20, -9, -45, 54, -1, 123, 84, -59, 55, 44, -104, 63, -11, -100};
            case "libiroh_java_osx_aarch_64_1f12e83.dylib":
                return new byte[]{46, 23, 1, 71, 59, -19, -127, -34, -90, 69, -63, 20, 62, 24, 22, -73, 2, 69, -7, -111, 68, 89, 65, -103, -94, 125, 57, -114, 126, -6, -3, -7};
            case "libiroh_java_osx_x86_64_1f12e83.dylib":
                return new byte[]{-109, -64, 95, -114, -9, -126, 41, -103, -5, -117, 32, 85, 18, 77, -81, -124, 91, 117, -47, 110, 8, 87, 16, 125, 83, -70, -2, 70, 4, 57, 95, 73};
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
