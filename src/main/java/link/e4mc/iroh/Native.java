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
                + "_57caf9a";

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
            case "iroh_java_windows_aarch_64_57caf9a.dll":
                return new byte[]{-128, 86, 116, 30, -49, -24, 98, -4, -61, 57, -106, -75, -9, -123, 73, 2, -121, 123, 19, -88, -22, -34, 29, -115, -119, -45, 33, -53, -94, 100, 45, -53};
            case "iroh_java_windows_x86_64_57caf9a.dll":
                return new byte[]{83, 66, -122, -48, 44, -36, 21, -106, -22, 96, 100, -44, 25, 50, -109, 34, -117, -82, -10, -108, -48, -120, -36, 125, -33, -109, 26, 59, -35, -111, 6, -21};
            case "libiroh_java_linux_aarch_64_57caf9a.so":
                return new byte[]{-116, 69, -87, -25, 76, -88, 63, -35, -46, -23, 55, 1, -127, -118, 80, 67, -87, 15, 94, 10, -51, -45, 108, -43, -40, -85, -21, 80, -120, -107, -17, 32};
            case "libiroh_java_linux_x86_64_57caf9a.so":
                return new byte[]{90, -87, -19, 3, 116, 42, -10, -45, 122, 7, -105, -60, 78, -5, 25, -54, 124, 72, 127, -61, -26, -52, -41, 105, 86, -77, -91, -114, -19, -97, 98, 70};
            case "libiroh_java_osx_aarch_64_57caf9a.dylib":
                return new byte[]{83, 56, -97, -126, 30, -49, 87, -89, -110, 77, -62, 61, -12, -51, -72, -91, 107, -88, -69, 10, -115, -44, 102, -42, -115, 27, -100, 114, -3, 94, 76, -128};
            case "libiroh_java_osx_x86_64_57caf9a.dylib":
                return new byte[]{101, 35, 49, -83, -32, 90, -65, 127, -115, 113, 104, -104, 23, -11, 58, 6, -127, 109, -113, 118, 66, 66, -12, 86, 127, -33, -128, 36, -98, 110, 50, -11};        }
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
