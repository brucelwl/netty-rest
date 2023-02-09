package com.lwl.httpserver.mvc;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Created by bruce on 2018/4/28 17:35
 * <pre>
 *       dependency: org.springframework:spring-core:5.3.18
 * </pre>
 */
public class ClassScanner {
    //保存过滤规则要排除的注解
    private final List<TypeFilter> includeFilters = new LinkedList<>();
    private final List<TypeFilter> excludeFilters = new LinkedList<>();

    private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
    private MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(this.resourcePatternResolver);

    /** 设置扫描被哪些注解标记的类 */
    public void addIncludeAnnotation(Class<? extends Annotation> annotation) {
        this.addIncludeFilter(new AnnotationTypeFilter(annotation));
    }

    public void addIncludeFilter(TypeFilter includeFilter) {
        this.includeFilters.add(includeFilter);
    }

    public void addExcludeFilter(TypeFilter excludeFilter) {
        this.excludeFilters.add(0, excludeFilter);
    }

    public Set<Class<?>> scan(String... basePackages) {
        Set<Class<?>> classes = new HashSet<>();
        for (String basePackage : basePackages) {
            classes.addAll(this.doScan(basePackage));
        }
        return classes;
    }

    public Set<Class<?>> doScan(String basePackage) {
        Set<Class<?>> classes = new HashSet<>();
        try {
            String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                    + ClassUtils.convertClassNameToResourcePath(basePackage) + "/**/*.class";
            Resource[] resources = this.resourcePatternResolver.getResources(packageSearchPath);

            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    MetadataReader metadataReader = this.metadataReaderFactory.getMetadataReader(resource);
                    if ((includeFilters.size() == 0 && excludeFilters.size() == 0) || matches(metadataReader)) {
                        classes.add(ClassUtils.forName(metadataReader.getClassMetadata().getClassName(), null));
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("I/O failure during classpath scanning", ex);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return classes;
    }

    private boolean matches(MetadataReader metadataReader) throws IOException {
        for (TypeFilter tf : this.excludeFilters) {
            if (tf.match(metadataReader, this.metadataReaderFactory)) {
                return false;
            }
        }
        for (TypeFilter tf : this.includeFilters) {
            if (tf.match(metadataReader, this.metadataReaderFactory)) {
                return true;
            }
        }
        return false;
    }


}
