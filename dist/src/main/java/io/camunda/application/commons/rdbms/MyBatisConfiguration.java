/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.db.rdbms.LiquibaseSchemaManager;
import io.camunda.db.rdbms.NoopSchemaManager;
import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.sql.AuthorizationMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.ClusterVariableMapper;
import io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.FormMapper;
import io.camunda.db.rdbms.sql.GlobalListenerMapper;
import io.camunda.db.rdbms.sql.GroupMapper;
import io.camunda.db.rdbms.sql.HistoryDeletionMapper;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.sql.JobMetricsBatchMapper;
import io.camunda.db.rdbms.sql.MappingRuleMapper;
import io.camunda.db.rdbms.sql.MessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.PersistentWebSessionMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.sql.RoleMapper;
import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.db.rdbms.sql.TableMetricsMapper;
import io.camunda.db.rdbms.sql.TenantMapper;
import io.camunda.db.rdbms.sql.UsageMetricMapper;
import io.camunda.db.rdbms.sql.UsageMetricTUMapper;
import io.camunda.db.rdbms.sql.UserMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Import(DataSourceAutoConfiguration.class)
public class MyBatisConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(MyBatisConfiguration.class);

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.data.secondary-storage.rdbms",
      name = "auto-ddl",
      havingValue = "true",
      matchIfMissing = true)
  public RdbmsSchemaManager rdbmsExporterLiquibase(
      final DataSource dataSource,
      final VendorDatabaseProperties vendorDatabaseProperties,
      @Value("${camunda.data.secondary-storage.rdbms.prefix:}") final String prefix) {
    final String trimmedPrefix = StringUtils.trimToEmpty(prefix);
    LOGGER.info(
        "Initializing Liquibase for RDBMS with global table trimmedPrefix '{}'.", trimmedPrefix);

    final var moduleConfig = new LiquibaseSchemaManager();
    moduleConfig.setDataSource(dataSource);
    moduleConfig.setDatabaseChangeLogTable(trimmedPrefix + "DATABASECHANGELOG");
    moduleConfig.setDatabaseChangeLogLockTable(trimmedPrefix + "DATABASECHANGELOGLOCK");
    moduleConfig.setParameters(
        Map.of(
            "prefix",
            trimmedPrefix,
            "userCharColumnSize",
            Integer.toString(vendorDatabaseProperties.userCharColumnSize()),
            "errorMessageSize",
            Integer.toString(vendorDatabaseProperties.errorMessageSize()),
            "treePathSize",
            Integer.toString(vendorDatabaseProperties.treePathSize())));
    // changelog file located in src/main/resources directly in the module
    moduleConfig.setChangeLog("db/changelog/rdbms-exporter/changelog-master.xml");

    return moduleConfig;
  }

  @Bean
  @ConditionalOnMissingBean(RdbmsSchemaManager.class)
  public RdbmsSchemaManager rdbmsNoopSchemaManager() {
    return new NoopSchemaManager();
  }

  @Bean
  public RdbmsDatabaseIdProvider databaseIdProvider(
      @Value("${camunda.data.secondary-storage.rdbms.database-vendor-id:}") final String vendorId) {
    return new RdbmsDatabaseIdProvider(vendorId);
  }

  @Bean
  public VendorDatabaseProperties databaseProperties(
      final DataSource dataSource, final RdbmsDatabaseIdProvider databaseIdProvider)
      throws IOException {
    final var databaseId = databaseIdProvider.getDatabaseId(dataSource);
    LOGGER.info("Detected databaseId: {}", databaseId);

    return VendorDatabasePropertiesLoader.load(databaseId);
  }

  @Bean
  public SqlSessionFactory sqlSessionFactory(
      final DataSource dataSource,
      final DatabaseIdProvider databaseIdProvider,
      final VendorDatabaseProperties databaseProperties,
      @Value("${camunda.data.secondary-storage.rdbms.prefix:}") final String prefix)
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

  @Bean
  public MapperFactoryBean<AuthorizationMapper> authorizationMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, AuthorizationMapper.class);
  }

  @Bean
  public MapperFactoryBean<AuditLogMapper> auditLogMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, AuditLogMapper.class);
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
  public MapperFactoryBean<ClusterVariableMapper> clusterVariableMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, ClusterVariableMapper.class);
  }

  @Bean
  public MapperFactoryBean<JobMetricsBatchMapper> jobMetricsBatchMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, JobMetricsBatchMapper.class);
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
  public MapperFactoryBean<MappingRuleMapper> mappingMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, MappingRuleMapper.class);
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
  public MapperFactoryBean<BatchOperationMapper> batchOperationMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, BatchOperationMapper.class);
  }

  @Bean
  public MapperFactoryBean<JobMapper> jobMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, JobMapper.class);
  }

  @Bean
  public MapperFactoryBean<SequenceFlowMapper> sequenceFlowMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, SequenceFlowMapper.class);
  }

  @Bean
  public MapperFactoryBean<UsageMetricMapper> usageMetricMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, UsageMetricMapper.class);
  }

  @Bean
  public MapperFactoryBean<UsageMetricTUMapper> usageMetricTUMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, UsageMetricTUMapper.class);
  }

  @Bean
  MapperFactoryBean<MessageSubscriptionMapper> messageSubscriptionMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, MessageSubscriptionMapper.class);
  }

  @Bean
  MapperFactoryBean<CorrelatedMessageSubscriptionMapper> correlatedMessageSubscriptionMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, CorrelatedMessageSubscriptionMapper.class);
  }

  @Bean
  MapperFactoryBean<TableMetricsMapper> tableMetricsMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, TableMetricsMapper.class);
  }

  @Bean
  MapperFactoryBean<HistoryDeletionMapper> historyDeletionMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, HistoryDeletionMapper.class);
  }

  @Bean
  MapperFactoryBean<PersistentWebSessionMapper> persistentWebSessionMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, PersistentWebSessionMapper.class);
  }

  @Bean
  public MapperFactoryBean<GlobalListenerMapper> globalListenerMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, GlobalListenerMapper.class);
  }

  private <T> MapperFactoryBean<T> createMapperFactoryBean(
      final SqlSessionFactory sqlSessionFactory, final Class<T> clazz) {
    final MapperFactoryBean<T> factoryBean = new MapperFactoryBean<>(clazz);
    factoryBean.setSqlSessionFactory(sqlSessionFactory);
    return factoryBean;
  }
}
