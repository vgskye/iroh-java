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
                + "_928d48a";

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
            case "iroh_java_windows_x86_64_928d48a.dll":
                return new byte[]{-86, -20, 111, -80, 123, -107, -68, -48, -38, 110, -93, -126, 36, 21, -104, -55, -51, -45, -67, 8, -125, -10, 114, -128, -2, -24, 83, 19, 10, 58, 24, -103};
            case "libiroh_java_linux_x86_64_928d48a.so":
                return new byte[]{81, -36, -83, -78, 47, 44, -94, -66, -26, 7, -109, 2, 8, 122, -6, -92, -33, -86, 61, -81, 40, -54, 69, 88, 63, -96, -117, -116, 22, 112, 53, -64};
            case "libiroh_java_osx_aarch_64_928d48a.dylib":
                return new byte[]{-1, 88, -48, -2, -29, 50, -6, -63, -85, -79, -117, 78, 85, -86, 2, 89, 118, -35, -4, -77, -63, -45, 0, -15, 107, -12, -96, -74, 79, -22, 63, 39};
            case "libiroh_java_osx_x86_64_928d48a.dylib":
                return new byte[]{-10, 75, -67, 11, -110, -104, -116, 93, -50, 16, -6, -125, -39, -125, -25, -16, 96, 90, -53, -40, 89, -33, -48, 17, 77, 63, 41, -33, -114, 24, 5, -53};
        }
        return new byte[]{};
    }

    static native Object resolveDeferredInitializer(DeferredInitializer<?> deferredInitializer);
    static native void initEndpointBundle(Endpoint bundle, byte[][] alpns, String[] relays);
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
