/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static io.camunda.configuration.api.physicaltenants.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.db.rdbms.DefaultRdbmsSchemaManagerRegistry;
import io.camunda.db.rdbms.PerTenantSchemaConfig;
import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.PersistentWebSessionMapper;
import io.camunda.db.rdbms.sql.ReplicationStatusMapper;
import io.camunda.db.rdbms.sql.TableMetricsMapper;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.OffsetDateTimeTypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class MyBatisConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(MyBatisConfiguration.class);

  @Bean
  public RdbmsSchemaManagerRegistry rdbmsSchemaManagerRegistry(
      final RdbmsDataSources rdbmsDataSources,
      final PhysicalTenantResolver physicalTenantResolver) {
    final Map<String, PerTenantSchemaConfig> physicalTenantConfigs = new LinkedHashMap<>();
    for (final String physicalTenantId : physicalTenantResolver.getAll().keySet()) {
      final var rdbms =
          physicalTenantResolver
              .forPhysicalTenant(physicalTenantId)
              .getData()
              .getSecondaryStorage()
              .getRdbms();
      final var trimmedPrefix = StringUtils.trimToEmpty(rdbms.getPrefix());
      LOGGER.info(
          "Initializing Liquibase for physical RDBMS tenant '{}' with table prefix '{}'.",
          physicalTenantId,
          trimmedPrefix);
      physicalTenantConfigs.put(
          physicalTenantId,
          new PerTenantSchemaConfig(
              rdbmsDataSources.dataSourceFor(physicalTenantId),
              rdbmsDataSources.vendorPropertiesFor(physicalTenantId),
              trimmedPrefix,
              rdbms.getAutoDdl(),
              rdbms.getDdlLockWaitTimeout()));
    }
    // VersionUtil.getVersion() may not be a valid semantic version during local development;
    // the schema-version check is skipped in that case.
    return DefaultRdbmsSchemaManagerRegistry.fromConfigs(
        physicalTenantConfigs, VersionUtil.getVersion());
  }

  @Bean
  public RdbmsDataSources rdbmsDataSources(final PhysicalTenantResolver physicalTenantResolver)
      throws IOException {
    return RdbmsDataSources.of(
        physicalTenantResolver.mapValues(
            camunda -> camunda.getData().getSecondaryStorage().getRdbms()));
  }

  @Bean
  public Map<String, SqlSessionFactory> sqlSessionFactories(
      final RdbmsDataSources rdbmsDataSources, final PhysicalTenantResolver physicalTenantResolver)
      throws Exception {
    final var factories = new LinkedHashMap<String, SqlSessionFactory>();
    for (final var tenantId : rdbmsDataSources.physicalTenantIds()) {
      final var prefix =
          physicalTenantResolver
              .forPhysicalTenant(tenantId)
              .getData()
              .getSecondaryStorage()
              .getRdbms()
              .getPrefix();
      factories.put(
          tenantId,
          buildSqlSessionFactory(
              rdbmsDataSources.dataSourceFor(tenantId),
              rdbmsDataSources.databaseIdProviderFor(tenantId),
              rdbmsDataSources.vendorPropertiesFor(tenantId),
              prefix));
    }
    return Map.copyOf(factories);
  }

  @Bean
  public Map<String, RdbmsMapperBundle> rdbmsMapperBundles(
      final Map<String, SqlSessionFactory> sqlSessionFactories,
      final RdbmsDataSources rdbmsDataSources) {
    final var bundles = new LinkedHashMap<String, RdbmsMapperBundle>();
    for (final var entry : sqlSessionFactories.entrySet()) {
      final var physicalTenantId = entry.getKey();
      final var factory = entry.getValue();
      bundles.put(
          physicalTenantId,
          RdbmsMapperBundle.from(
              factory,
              new SqlSessionTemplate(factory),
              rdbmsDataSources.vendorPropertiesFor(physicalTenantId)));
    }
    return Map.copyOf(bundles);
  }

  private SqlSessionFactory buildSqlSessionFactory(
      final DataSource dataSource,
      final DatabaseIdProvider databaseIdProvider,
      final VendorDatabaseProperties databaseProperties,
      final String prefix)
      throws Exception {
    final var configuration = new org.apache.ibatis.session.Configuration();
    configuration.setJdbcTypeForNull(JdbcType.NULL);
    configuration.getTypeHandlerRegistry().register(OffsetDateTimeTypeHandler.class);

    final SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setConfiguration(configuration);
    factoryBean.setDataSource(dataSource);
    factoryBean.setDatabaseIdProvider(databaseIdProvider);
    factoryBean.addMapperLocations(
        new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));

    final Properties p = new Properties();
    p.put("prefix", StringUtils.trimToEmpty(prefix));
    p.putAll(databaseProperties.properties());
    factoryBean.setConfigurationProperties(p);
    return factoryBean.getObject();
  }

  // TODO: the next 3 mappers are the last remaining default-tenant-only mappers. They should be
  // refactored to support multi-tenancy, and then these beans can be removed.
  @Bean
  TableMetricsMapper tableMetricsMapper(
      final Map<String, RdbmsMapperBundle> allRdbmsMapperBundles) {
    return allRdbmsMapperBundles.get(DEFAULT_PHYSICAL_TENANT_ID).tableMetricsMapper();
  }

  @Bean
  ReplicationStatusMapper replicationStatusMapper(
      final Map<String, RdbmsMapperBundle> allRdbmsMapperBundles) {
    return allRdbmsMapperBundles.get(DEFAULT_PHYSICAL_TENANT_ID).replicationStatusMapper();
  }

  @Bean
  PersistentWebSessionMapper persistentWebSessionMapper(
      final Map<String, RdbmsMapperBundle> allRdbmsMapperBundles) {
    return allRdbmsMapperBundles.get(DEFAULT_PHYSICAL_TENANT_ID).persistentWebSessionMapper();
  }
}
