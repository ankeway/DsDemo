package com.example.dsdemo;

import java.util.Collection;
import java.util.Objects;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceUnwrapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({MetricsAutoConfiguration.class, DataSourceAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class})
@ConditionalOnClass({DataSource.class, MeterRegistry.class, DynamicRoutingDataSource.class, ItemDataSource.class})
@ConditionalOnBean({DataSource.class, MeterRegistry.class})
public class DynamicRoutingDataSourceMetricsAutoConfiguration {
	
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({DynamicRoutingDataSource.class, ItemDataSource.class, HikariDataSource.class})
	static class DynamicRoutingDataSourceMetricsConfiguration {
		
		private final MeterRegistry registry;

		private final Logger log = LoggerFactory.getLogger(DynamicRoutingDataSourceMetricsAutoConfiguration.class);
		
		DynamicRoutingDataSourceMetricsConfiguration(MeterRegistry registry) {
			this.registry = registry;
		}

		@Autowired
		void bindMetricsRegistryToDataSources(DataSource dataSource) {
			
			// if add like javamelody-spring-boot-starter , dataSource become net.bull.javamelody.JdbcWrapper$DelegatingInvocationHandler 
			// or like JdkDynamicAopProxy  a dynamic proxy,
			// so try to safeUnwrap JdbcWrapper or JdkDynamicAopProxy and all of them are not a isInterface 
			// but spring boot 2.3.4, don't have target.isInterface(), DataSourceUnwrapper.unwrap(JdbcWrapper, DynamicRoutingDataSource.class) return DynamicRoutingDataSource;
			// we have to use custome DynamicRoutingDataSourceUnwrapper solve this problem
			
			DynamicRoutingDataSource dynamicRoutingDataSource = DynamicRoutingDataSourceUnwrapper.unwrap(dataSource, DynamicRoutingDataSource.class);
			if (Objects.isNull(dynamicRoutingDataSource) || CollectionUtils.isEmpty(dynamicRoutingDataSource.getCurrentDataSources())) {
				return;
			}
			Collection<DataSource> dynamicDataSources = dynamicRoutingDataSource.getCurrentDataSources().values();
			for (DataSource dynamicDataSource : dynamicDataSources) {
				ItemDataSource itemDataSource = DataSourceUnwrapper.unwrap(dynamicDataSource, ItemDataSource.class);
				if (Objects.isNull(itemDataSource) || Objects.isNull(itemDataSource.getRealDataSource())) {
					continue;
				}
				if (itemDataSource.getRealDataSource() instanceof HikariDataSource) {
					HikariDataSource hikariDataSource = DataSourceUnwrapper.unwrap(itemDataSource.getRealDataSource(), HikariDataSource.class);
					bindMetricsRegistryToHikariDataSource(hikariDataSource);
				} 
			}
		}

		private void bindMetricsRegistryToHikariDataSource(HikariDataSource hikari) {
			if (hikari.getMetricRegistry() == null && hikari.getMetricsTrackerFactory() == null) {
				try {
					hikari.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(this.registry));
				} catch (Exception ex) {
					log.warn("Failed to bind Hikari metrics: {}", ex.getMessage(), ex);
				}
			}
		}
		
		
	}
	
}
