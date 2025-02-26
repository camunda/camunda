/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.AuthorizationMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.FormMapper;
import io.camunda.db.rdbms.sql.GroupMapper;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.MappingMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.sql.RoleMapper;
import io.camunda.db.rdbms.sql.TenantMapper;
import io.camunda.db.rdbms.sql.UserMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.OffsetDateTimeTypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Import(DataSourceAutoConfiguration.class)
public class MyBatisConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(MyBatisConfiguration.class);

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.database",
      name = "auto-ddl",
      havingValue = "true",
      matchIfMissing = true)
  public MultiTenantSpringLiquibase rdbmsExporterLiquibase(
      final DataSource dataSource,
      @Value("${camunda.database.index-prefix:}") final String indexPrefix) {
    final String prefix = StringUtils.trimToEmpty(indexPrefix);
    LOGGER.info("Initializing Liquibase for RDBMS with global table prefix '{}'.", prefix);

    final var moduleConfig = new MultiTenantSpringLiquibase();
    moduleConfig.setDataSource(dataSource);
    moduleConfig.setDatabaseChangeLogTable(prefix + "DATABASECHANGELOG");
    moduleConfig.setDatabaseChangeLogLockTable(prefix + "DATABASECHANGELOGLOCK");
    moduleConfig.setParameters(Map.of("prefix", prefix));
    // changelog file located in src/main/resources directly in the module
    moduleConfig.setChangeLog("db/changelog/rdbms-exporter/changelog-master.xml");

    return moduleConfig;
  }

  @Bean
  public RdbmsDatabaseIdProvider databaseIdProvider(
      @Value("${camunda.database.database-vendor-id:}") final String vendorId) {
    return new RdbmsDatabaseIdProvider(vendorId);
  }

  @Bean
  public VendorDatabaseProperties databaseProperties(
      final DataSource dataSource, final RdbmsDatabaseIdProvider databaseIdProvider)
      throws IOException {
    final var databaseId = databaseIdProvider.getDatabaseId(dataSource);
    LOGGER.info("Detected databaseId: {}", databaseId);

    final Properties properties = new Properties();
    final var file = "db/vendor-properties/" + databaseId + ".properties";
    try (final var propertiesInputStream = getClass().getClassLoader().getResourceAsStream(file)) {
      if (propertiesInputStream != null) {
        properties.load(propertiesInputStream);
      } else {
        throw new IllegalArgumentException(
            "No vendor properties found for databaseId " + databaseId);
      }
    }

    return new VendorDatabaseProperties(properties);
  }

  @Bean
  public SqlSessionFactory sqlSessionFactory(
      final DataSource dataSource,
      final DatabaseIdProvider databaseIdProvider,
      final VendorDatabaseProperties databaseProperties,
      @Value("${camunda.database.index-prefix:}") final String indexPrefix)
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
    p.put("prefix", StringUtils.trimToEmpty(indexPrefix));
    p.putAll(databaseProperties.properties());
    factoryBean.setConfigurationProperties(p);
    return factoryBean.getObject();
  }

  @Bean
  public MapperFactoryBean<AuthorizationMapper> authorizationMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, AuthorizationMapper.class);
  }

  @Bean
  public MapperFactoryBean<DecisionDefinitionMapper> decisionDefinitionMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, DecisionDefinitionMapper.class);
  }

  @Bean
  public MapperFactoryBean<DecisionInstanceMapper> decisionInstanceMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, DecisionInstanceMapper.class);
  }

  @Bean
  public MapperFactoryBean<DecisionRequirementsMapper> decisionRequirementsMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, DecisionRequirementsMapper.class);
  }

  @Bean
  public MapperFactoryBean<FlowNodeInstanceMapper> flowNodeInstanceMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, FlowNodeInstanceMapper.class);
  }

  @Bean
  public MapperFactoryBean<GroupMapper> groupInstanceMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, GroupMapper.class);
  }

  @Bean
  public MapperFactoryBean<IncidentMapper> incidentMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, IncidentMapper.class);
  }

  @Bean
  public MapperFactoryBean<ProcessInstanceMapper> processInstanceMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, ProcessInstanceMapper.class);
  }

  @Bean
  public MapperFactoryBean<ProcessDefinitionMapper> processDeploymentMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, ProcessDefinitionMapper.class);
  }

  @Bean
  public MapperFactoryBean<TenantMapper> tenantMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, TenantMapper.class);
  }

  @Bean
  public MapperFactoryBean<VariableMapper> variableMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, VariableMapper.class);
  }

  @Bean
  public MapperFactoryBean<RoleMapper> roleMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, RoleMapper.class);
  }

  @Bean
  public MapperFactoryBean<UserMapper> userMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, UserMapper.class);
  }

  @Bean
  public MapperFactoryBean<UserTaskMapper> userTaskMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, UserTaskMapper.class);
  }

  @Bean
  public MapperFactoryBean<FormMapper> formMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, FormMapper.class);
  }

  @Bean
  public MapperFactoryBean<MappingMapper> mappingMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, MappingMapper.class);
  }

  @Bean
  public MapperFactoryBean<ExporterPositionMapper> exporterPosition(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, ExporterPositionMapper.class);
  }

  @Bean
  public MapperFactoryBean<PurgeMapper> purgeMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, PurgeMapper.class);
  }

  @Bean
  public MapperFactoryBean<BatchOperationMapper> batchOperationMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, BatchOperationMapper.class);
  }

  private <T> MapperFactoryBean<T> createMapperFactoryBean(
      final SqlSessionFactory sqlSessionFactory, final Class<T> clazz) {
    final MapperFactoryBean<T> factoryBean = new MapperFactoryBean<>(clazz);
    factoryBean.setSqlSessionFactory(sqlSessionFactory);
    return factoryBean;
  }
}
