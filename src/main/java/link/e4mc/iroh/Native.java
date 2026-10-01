package link.e4mc.iroh;

import dev.dirs.ProjectDirectories;
import io.netty.util.internal.PlatformDependent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
                + "_b296210";

        String libraryPath = System.getProperty("link.e4mc.dialtone.native_path");
        boolean downloaded = false;

        while (libraryPath == null) {
            String fileName = System.mapLibraryName(libName);
            String legacyFolderPath = System.getProperty("user.home") + File.separatorChar + ".e4mc_cache";
            String folderPath;
            try {
                folderPath = ProjectDirectories.from("link", "e4mc", "e4mc").cacheDir;
            } catch (Throwable e) {
                folderPath = legacyFolderPath;
            }
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
            case "iroh_java_windows_aarch_64_b296210.dll":
                return new byte[]{-93, 53, -121, 45, -17, -78, 41, -54, 106, 48, 74, -118, 0, 95, 101, -81, 71, 115, 118, 111, 38, 50, -68, 55, 73, -73, -81, -83, -106, -107, -111, 44};
            case "iroh_java_windows_x86_64_b296210.dll":
                return new byte[]{-8, 96, 94, 29, -46, -127, -102, 53, 29, 14, -66, -50, -1, 71, -26, 46, -40, 127, -67, 34, -40, -8, 47, -94, -128, 61, 56, -2, 34, -118, -73, -52};
            case "libiroh_java_linux_aarch_64_b296210.so":
                return new byte[]{118, 23, 21, -112, 49, -41, -98, -107, 24, 75, 9, -24, 38, -58, -61, -108, 116, 7, 80, -91, -72, 39, -102, -65, -79, 64, -90, 16, -16, 1, -121, -104};
            case "libiroh_java_linux_x86_64_b296210.so":
                return new byte[]{27, 102, 111, -81, -48, 117, -116, 28, -75, -110, -68, 27, 22, -76, -74, -75, 3, 60, 97, 79, -84, -55, 76, 63, -14, -73, 63, 83, 13, 8, 66, 66};
            case "libiroh_java_osx_aarch_64_b296210.dylib":
                return new byte[]{20, -3, -63, -46, -124, 10, 99, 105, -34, 6, -57, 45, 16, 103, 4, 59, 22, 49, 73, 114, 50, 109, 90, 32, 85, -14, -34, -79, 28, -34, -15, 60};
            case "libiroh_java_osx_x86_64_b296210.dylib":
                return new byte[]{31, 40, 27, -121, 92, -83, 113, 24, -20, 13, 114, 126, 69, 85, 112, -116, -21, -100, 74, 3, -15, -41, -114, 18, 116, 43, -65, 83, -63, -107, 106, -19};
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
    static native String debugInfo(Connection connection);
}
