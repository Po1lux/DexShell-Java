import sun.security.provider.SHA;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.Adler32;

/**
 * @author pollux
 * @version 1.0.0
 * @date 2020/1/30 9:50 下午
 */
public class ShellToolsMain {

    public static void main(String[] args) {
        try {
            File sourceAPk = new File("iterms/source.apk");
            File shellDex = new File("iterms/classes.dex");
            byte[] sourceApkB = encrypt(readByteFromFile(sourceAPk));
            byte[] shellDexB = readByteFromFile(shellDex);
            int sourceApkLen = sourceApkB.length;
            int shellDexLen = shellDexB.length;
            byte[] newDex = new byte[sourceApkLen + shellDexLen + 4];
            System.arraycopy(shellDexB, 0, newDex, 0, shellDexLen);
            System.arraycopy(sourceApkB, 0, newDex, shellDexLen, sourceApkLen);
            System.arraycopy(int2Byte(sourceApkLen), 0, newDex, shellDexLen + sourceApkLen, 4);
            fixDexHeaderSize(newDex);
            fixDexSignature(newDex);
            fixDexChecksum(newDex);
            String newDexPath = "iterms/newclasses.dex";
            File newDexFile = new File(newDexPath);
            if (!newDexFile.exists())
                newDexFile.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(newDexPath);
            fileOutputStream.write(newDex);
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }


    }


    private static byte[] encrypt(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (data[i] ^ 0xff);
        }
        return data;
    }

    private static byte[] int2Byte(int n) {
        byte[] buf = new byte[4];
        for (int i = 3; i >= 0; i--) {
            buf[i] = (byte) (n % 256);//256 = 0x100h
            n >>= 8;
        }
        return buf;
    }

    private static byte[] readByteFromFile(File file) throws IOException {
        byte[] buf = new byte[104];
        int nums = 0;
        FileInputStream fileInputStream = new FileInputStream(file);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        while (true) {
            nums = fileInputStream.read(buf);
            if (nums != -1) {
                byteArrayOutputStream.write(buf, 0, nums);
            } else {
                return byteArrayOutputStream.toByteArray();
            }
        }
    }

    private static void fixDexHeaderSize(byte[] newDex) {
        byte[] newSizeB = int2Byte(newDex.length);
        byte[] tmp = new byte[4];
        //转成little-endian
        for (int i = 0; i < 4; i++) {
            tmp[i] = newSizeB[3 - i];
        }
        System.arraycopy(tmp, 0, newDex, 0x20, 4);
    }

    private static void fixDexSignature(byte[] newDex) throws NoSuchAlgorithmException {
        byte[] newSignature = new byte[0x14];
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
        messageDigest.update(newDex, 0x20, newDex.length - 0x20);
        newSignature = messageDigest.digest();
        System.arraycopy(newSignature, 0, newDex, 0xc, 0x14);
        StringBuilder str = new StringBuilder();
        for (byte b : newSignature)
            str.append(Integer.toString(b & 0xff, 16));
        System.out.println("new signature:" + str);
    }

    private static void fixDexChecksum(byte[] newDex) {
        byte[] newChecksum = new byte[4];
        Adler32 adler32 = new Adler32();
        adler32.update(newDex, 0xc, newDex.length - 0xc);
        long valueL = adler32.getValue();
        int valueI = (int) valueL;
        newChecksum = int2Byte(valueI);
        byte[] tmp = new byte[4];
        //convert to little endian
        for (int i = 0; i < 4; i++) {
            tmp[i] = newChecksum[3 - i];
        }
        System.arraycopy(tmp, 0, newDex, 0x8, 0x4);
        System.out.println("new checksum: " + Long.toHexString(valueL));
    }
}
