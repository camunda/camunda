/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.db.rdbms.PerTenantSchemaConfig;
import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.db.rdbms.RdbmsSchemaManagers;
import io.camunda.db.rdbms.RdbmsSchemaMigrationStatusProvider;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
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

  /**
   * The registry every consumer of "is this tenant's schema ready" resolves — the RDBMS exporter,
   * the request-time rejection path and the per-tenant readiness gauge — and, on a multi-tenant
   * node, the bean that initializes each tenant's schema in isolation.
   */
  @Bean
  public RdbmsSchemaManagerRegistry rdbmsSchemaManagerRegistry(
      final RdbmsDataSources rdbmsDataSources,
      final PhysicalTenantResolver physicalTenantResolver) {
    // VersionUtil.getVersion() may not be a valid semantic version during local development;
    // the schema-version check is skipped in that case.
    return new RdbmsSchemaInitializer(
        RdbmsSchemaManagers.fromConfigs(
            physicalTenantSchemaConfigs(rdbmsDataSources, physicalTenantResolver),
            VersionUtil.getVersion()));
  }

  /**
   * Reports whether every physical tenant's RDBMS schema has migrated to the running application
   * version, for the upgrade-readiness endpoint (camunda/product-hub#3067). Built from the same
   * per-tenant configs as {@link #rdbmsSchemaManagerRegistry}, but independently — this needs to
   * read the schema version regardless of whether this application's own Liquibase run applied it
   * or an operator's external tooling did.
   */
  @Bean
  public RdbmsSchemaMigrationStatusProvider rdbmsSchemaMigrationStatusProvider(
      final RdbmsDataSources rdbmsDataSources,
      final PhysicalTenantResolver physicalTenantResolver) {
    return RdbmsSchemaMigrationStatusProvider.fromConfigs(
        physicalTenantSchemaConfigs(rdbmsDataSources, physicalTenantResolver),
        VersionUtil.getVersion());
  }

  private Map<String, PerTenantSchemaConfig> physicalTenantSchemaConfigs(
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
    return physicalTenantConfigs;
  }

  @Bean
  public RdbmsDataSources rdbmsDataSources(
      final PhysicalTenantResolver physicalTenantResolver, final MeterRegistry meterRegistry)
      throws IOException {
    return RdbmsDataSources.of(
        physicalTenantResolver.mapValues(
            camunda -> camunda.getData().getSecondaryStorage().getRdbms()),
        meterRegistry);
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
      final VendorDatabaseProperties databaseProperties,
      final String prefix)
      throws Exception {
    final var configuration = new org.apache.ibatis.session.Configuration();
    configuration.setJdbcTypeForNull(JdbcType.NULL);
    configuration.getTypeHandlerRegistry().register(OffsetDateTimeTypeHandler.class);
    // Which vendor's mapper statements apply is already settled in the vendor properties, so it is
    // set here rather than handed to a DatabaseIdProvider that would look it up over a connection
    // a second time. SqlSessionFactoryBean would only call setDatabaseId on this same object.
    configuration.setDatabaseId(databaseProperties.databaseId());

    final SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setConfiguration(configuration);
    factoryBean.setDataSource(dataSource);
    factoryBean.addMapperLocations(
        new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));

    final Properties p = new Properties();
    p.put("prefix", StringUtils.trimToEmpty(prefix));
    p.putAll(databaseProperties.properties());
    factoryBean.setConfigurationProperties(p);
    return factoryBean.getObject();
  }
}
