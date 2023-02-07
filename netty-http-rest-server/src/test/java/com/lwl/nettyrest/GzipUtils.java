package com.lwl.nettyrest;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipUtils {

    /** 压缩 */
    public static byte[] gzip(byte[] source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzipOutputStream = null;
        byte[] target = null;
        try {
            gzipOutputStream = new GZIPOutputStream(out);
            gzipOutputStream.write(source);
            gzipOutputStream.finish();
            target = out.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (gzipOutputStream != null) {
                try {
                    gzipOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return target;
    }

    /** 解压缩 */
    public static byte[] unGzip(byte[] source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(source);
        GZIPInputStream gzipInputStream = null;
        byte[] target = null;
        try {
            gzipInputStream = new GZIPInputStream(in);
            byte[] temp = new byte[1024];
            int length = 0;
            while ((length = gzipInputStream.read(temp, 0, temp.length)) != -1) {
                out.write(temp, 0, length);
            }
            target = out.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (gzipInputStream != null) {
                try {
                    gzipInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return target;
    }

    public static byte[] gzipObject(Object obj) throws IOException {
        ByteArrayOutputStream objBytes = new ByteArrayOutputStream();
        ObjectOutputStream objectOut = new ObjectOutputStream(objBytes);
        objectOut.writeObject(obj);

        System.out.println(objBytes.size());

        ByteArrayOutputStream gzipOut = new ByteArrayOutputStream();
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(gzipOut);
        gzipOutputStream.write(objBytes.toByteArray());
        gzipOutputStream.finish();

        System.out.println(gzipOut.size());
        return gzipOut.toByteArray();
    }


}
