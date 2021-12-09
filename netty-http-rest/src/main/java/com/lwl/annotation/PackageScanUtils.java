package com.lwl.annotation;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public class PackageScanUtils {

    /** 从指定的package中获取所有的Class (包括子包) */
    public Set<Class<?>> getClassesInPackage(String pack, boolean scanJar) {
        if (pack == null || "".equals(pack.trim())) {
            throw new RuntimeException("the package [ " + pack + " ] not exists");
        }
        Set<Class<?>> classesSet = new LinkedHashSet<>();

        // 获取包的名字 并进行替换
        String packageDirName = pack.replace('.', '/');
        try {
            Enumeration<URL> dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
            if (!dirs.hasMoreElements()) {
                throw new RuntimeException("the package [ " + pack + " ] not exists");
            }
            // 循环迭代下去
            while (dirs.hasMoreElements()) {
                URL url = dirs.nextElement();
                String protocol = url.getProtocol();
                // 如果是以文件的形式保存在服务器上
                if ("file".equals(protocol)) {
                    Logger.getLogger("lwl").info("file(class) type to be scanned");
                    // 获取包的物理路径
                    String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                    // 以文件的方式扫描整个包下的文件 并添加到集合中
                    collectorClassesInFilePackage(pack, filePath, classesSet);

                } else if (scanJar && "jar".equals(protocol)) {
                    collectorClassesInJarPackage(packageDirName, url, classesSet);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return classesSet;
    }

    private void collectorClassesInJarPackage(String packageDirName, URL url, Set<Class<?>> classesSet) throws IOException, ClassNotFoundException {
        Logger.getLogger("lwl").info("jar type to be scanned" + url.getFile());

        JarFile jar = ((JarURLConnection) url.openConnection()).getJarFile();
        Enumeration<JarEntry> entries = jar.entries();

        while (entries.hasMoreElements()) {
            // 获取jar里的一个实体 可以是目录 和一些jar包里的其他文件 如META-INF等文件
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith(packageDirName) && name.endsWith(".class") && !entry.isDirectory()) {
                String fullClassName = name.substring(0, name.length() - 6).replace('/', '.');
                classesSet.add(Thread.currentThread().getContextClassLoader().loadClass(fullClassName));
            }
        }
    }

    /**
     * 以文件的形式来获取包下的所有Class
     */
    private void collectorClassesInFilePackage(String packageName, String packagePath, Set<Class<?>> classes) throws ClassNotFoundException {
        // 获取此包的目录 建立一个File
        File dir = new File(packagePath);
        if (!dir.exists() || !dir.isDirectory()) {
            Logger.getLogger("lwl").info("异常信息 Exception " + packageName + " 可能不存在");
            return;
        }
        // 如果存在 就获取包下的所有.class文件和子目录
        File[] dirfiles = dir.listFiles(file -> file.isDirectory() || file.getName().endsWith(".class"));
        if (dirfiles == null) {
            return;
        }
        // 循环所有文件
        for (File file : dirfiles) {
            // 如果是目录 则继续扫描
            if (file.isDirectory()) {
                collectorClassesInFilePackage(packageName + "." + file.getName(), file.getAbsolutePath(), classes);
            } else {
                // 如果是java类文件 去掉后面的.class 只留下类名
                String className = file.getName().substring(0, file.getName().length() - 6);

                classes.add(Thread.currentThread().getContextClassLoader().loadClass(packageName + '.' + className));
            }
        }
    }

}
