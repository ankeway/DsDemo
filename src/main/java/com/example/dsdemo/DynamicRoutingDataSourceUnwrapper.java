package com.example.dsdemo;

import java.sql.Wrapper;

import javax.sql.DataSource;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.util.ClassUtils;

public class DynamicRoutingDataSourceUnwrapper {
    private static final boolean DELEGATING_DATA_SOURCE_PRESENT = ClassUtils.isPresent(
            "org.springframework.jdbc.datasource.DelegatingDataSource", DynamicRoutingDataSourceUnwrapper.class.getClassLoader());

    private DynamicRoutingDataSourceUnwrapper() {
    }

    /**
     * Return an object that implements the given {@code target} type, unwrapping delegate
     * or proxy if necessary.
     * @param dataSource the datasource to handle
     * @param target the type that the result must implement
     * @param <T> the target type
     * @return an object that implements the target type or {@code null}
     */
    public static <T> T unwrap(DataSource dataSource, Class<T> target) {
        if (target.isInstance(dataSource)) {
            return target.cast(dataSource);
        }
        T unwrapped = safeUnwrap(dataSource, target);
        if (unwrapped != null) {
            return unwrapped;
        }
        if (DELEGATING_DATA_SOURCE_PRESENT) {
            DataSource targetDataSource = DynamicRoutingDataSourceUnwrapper.DelegatingDataSourceUnwrapper.getTargetDataSource(dataSource);
            if (targetDataSource != null) {
                return unwrap(targetDataSource, target);
            }
        }
        if (AopUtils.isAopProxy(dataSource)) {
            Object proxyTarget = AopProxyUtils.getSingletonTarget(dataSource);
            if (proxyTarget instanceof DataSource) {
                return unwrap((DataSource) proxyTarget, target);
            }
        }
        return null;
    }

    private static <S> S safeUnwrap(Wrapper wrapper, Class<S> target) {
        try {
            if (wrapper.isWrapperFor(target)) {
                return wrapper.unwrap(target);
            }
        } catch (Exception ex) {
            // Continue
        }
        return null;
    }

    private static class DelegatingDataSourceUnwrapper {

        private static DataSource getTargetDataSource(DataSource dataSource) {
            if (dataSource instanceof DelegatingDataSource) {
                return ((DelegatingDataSource) dataSource).getTargetDataSource();
            }
            return null;
        }

    }
}
