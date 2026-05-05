/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static io.camunda.application.commons.rdbms.RdbmsDataSources.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Rdbms;
import io.camunda.db.rdbms.LiquibaseSchemaManager;
import io.camunda.db.rdbms.NoopSchemaManager;
import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.sql.AuthorizationMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.ClusterVariableMapper;
import io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.sql.DeployedResourceMapper;
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
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import java.io.IOException;
import java.time.Duration;
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
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Import(DataSourceTransactionManagerAutoConfiguration.class)
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
      @Value("${camunda.data.secondary-storage.rdbms.prefix:}") final String prefix,
      @Value("${camunda.data.secondary-storage.rdbms.ddl-lock-wait-timeout:PT15M}")
          final Duration lockWaitTimeout) {
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
    moduleConfig.setDdlLockWaitTimeout(lockWaitTimeout);

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
  public RdbmsDataSources rdbmsDataSources(
      final Camunda configuration, final RdbmsDatabaseIdProvider databaseIdProvider)
      throws IOException {
    final Rdbms rdbms = configuration.getData().getSecondaryStorage().getRdbms();
    // Per-physical-tenant configuration (camunda.physical-tenants.*) is delivered as a separate
    // prerequisite. Until then, wire a single 'default' physical tenant from the cluster-wide
    // block.
    // TODO make use of TenantConnectConfigResolver
    final Map<String, Rdbms> physicalTenantConfigs = new LinkedHashMap<>();
    physicalTenantConfigs.put(DEFAULT_PHYSICAL_TENANT_ID, rdbms);
    return RdbmsDataSources.of(physicalTenantConfigs, databaseIdProvider);
  }

  @Bean
  public Map<String, SqlSessionFactory> sqlSessionFactories(
      final RdbmsDataSources rdbmsDataSources,
      final DatabaseIdProvider databaseIdProvider,
      @Value("${camunda.data.secondary-storage.rdbms.prefix:}") final String prefix)
      throws Exception {
    final var factories = new LinkedHashMap<String, SqlSessionFactory>();
    for (final var entry : rdbmsDataSources.dataSourcesByTenant().entrySet()) {
      final var tenantId = entry.getKey();
      final var properties = rdbmsDataSources.vendorPropertiesFor(tenantId);
      factories.put(
          tenantId,
          buildSqlSessionFactory(entry.getValue(), databaseIdProvider, properties, prefix));
    }
    return factories;
  }

  @Bean
  public Map<String, RdbmsMapperBundle> rdbmsMapperBundles(
      final Map<String, SqlSessionFactory> sqlSessionFactories) {
    final var bundles = new LinkedHashMap<String, RdbmsMapperBundle>();
    for (final var entry : sqlSessionFactories.entrySet()) {
      bundles.put(entry.getKey(), buildMapperBundle(entry.getValue()));
    }
    return bundles;
  }

  // The following beans expose the default physical tenant's DataSource,
  // VendorDatabaseProperties, and individual mapper instances so that downstream consumers
  // (rdbmsExporterLiquibase, RdbmsConfiguration readers, PersistentWebSessionWriter)
  // can keep injecting them as singletons. They will be removed in future PRs once those
  // consumers are migrated to per-tenant routing.

  @Bean
  public DataSource dataSource(final RdbmsDataSources rdbmsDataSources) {
    return rdbmsDataSources.dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public VendorDatabaseProperties databaseProperties(final RdbmsDataSources rdbmsDataSources) {
    return rdbmsDataSources.vendorPropertiesFor(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public AuditLogMapper auditLogMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).auditLogMapper();
  }

  @Bean
  public AuthorizationMapper authorizationMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).authorizationMapper();
  }

  @Bean
  public BatchOperationMapper batchOperationMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).batchOperationMapper();
  }

  @Bean
  public ClusterVariableMapper clusterVariableMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).clusterVariableMapper();
  }

  @Bean
  public CorrelatedMessageSubscriptionMapper correlatedMessageSubscriptionMapper(
      final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).correlatedMessageSubscriptionMapper();
  }

  @Bean
  public DecisionDefinitionMapper decisionDefinitionMapper(
      final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).decisionDefinitionMapper();
  }

  @Bean
  public DecisionInstanceMapper decisionInstanceMapper(
      final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).decisionInstanceMapper();
  }

  @Bean
  public DecisionRequirementsMapper decisionRequirementsMapper(
      final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).decisionRequirementsMapper();
  }

  @Bean
  public DeployedResourceMapper deployedResourceMapper(
      final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).deployedResourceMapper();
  }

  @Bean
  public ExporterPositionMapper exporterPositionMapper(
      final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).exporterPositionMapper();
  }

  @Bean
  public FlowNodeInstanceMapper flowNodeInstanceMapper(
      final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).flowNodeInstanceMapper();
  }

  @Bean
  public FormMapper formMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).formMapper();
  }

  @Bean
  public GlobalListenerMapper globalListenerMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).globalListenerMapper();
  }

  @Bean
  public GroupMapper groupMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).groupMapper();
  }

  @Bean
  public HistoryDeletionMapper historyDeletionMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).historyDeletionMapper();
  }

  @Bean
  public IncidentMapper incidentMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).incidentMapper();
  }

  @Bean
  public JobMapper jobMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).jobMapper();
  }

  @Bean
  public JobMetricsBatchMapper jobMetricsBatchMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).jobMetricsBatchMapper();
  }

  @Bean
  public MappingRuleMapper mappingRuleMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).mappingRuleMapper();
  }

  @Bean
  public MessageSubscriptionMapper messageSubscriptionMapper(
      final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).messageSubscriptionMapper();
  }

  @Bean
  public PersistentWebSessionMapper persistentWebSessionMapper(
      final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).persistentWebSessionMapper();
  }

  @Bean
  public ProcessDefinitionMapper processDefinitionMapper(
      final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).processDefinitionMapper();
  }

  @Bean
  public ProcessInstanceMapper processInstanceMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).processInstanceMapper();
  }

  @Bean
  public PurgeMapper purgeMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).purgeMapper();
  }

  @Bean
  public RoleMapper roleMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).roleMapper();
  }

  @Bean
  public SequenceFlowMapper sequenceFlowMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).sequenceFlowMapper();
  }

  @Bean
  public TableMetricsMapper tableMetricsMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).tableMetricsMapper();
  }

  @Bean
  public TenantMapper tenantMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).tenantMapper();
  }

  @Bean
  public UsageMetricMapper usageMetricMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).usageMetricMapper();
  }

  @Bean
  public UsageMetricTUMapper usageMetricTUMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).usageMetricTUMapper();
  }

  @Bean
  public UserMapper userMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).userMapper();
  }

  @Bean
  public UserTaskMapper userTaskMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).userTaskMapper();
  }

  @Bean
  public VariableMapper variableMapper(final Map<String, RdbmsMapperBundle> bundles) {
    return defaultBundle(bundles).variableMapper();
  }

  static SqlSessionFactory buildSqlSessionFactory(
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

  static RdbmsMapperBundle buildMapperBundle(final SqlSessionFactory sqlSessionFactory) {
    return new RdbmsMapperBundle(
        mapperOf(sqlSessionFactory, AuditLogMapper.class),
        mapperOf(sqlSessionFactory, AuthorizationMapper.class),
        mapperOf(sqlSessionFactory, BatchOperationMapper.class),
        mapperOf(sqlSessionFactory, ClusterVariableMapper.class),
        mapperOf(sqlSessionFactory, CorrelatedMessageSubscriptionMapper.class),
        mapperOf(sqlSessionFactory, DecisionDefinitionMapper.class),
        mapperOf(sqlSessionFactory, DecisionInstanceMapper.class),
        mapperOf(sqlSessionFactory, DecisionRequirementsMapper.class),
        mapperOf(sqlSessionFactory, DeployedResourceMapper.class),
        mapperOf(sqlSessionFactory, ExporterPositionMapper.class),
        mapperOf(sqlSessionFactory, FlowNodeInstanceMapper.class),
        mapperOf(sqlSessionFactory, FormMapper.class),
        mapperOf(sqlSessionFactory, GlobalListenerMapper.class),
        mapperOf(sqlSessionFactory, GroupMapper.class),
        mapperOf(sqlSessionFactory, HistoryDeletionMapper.class),
        mapperOf(sqlSessionFactory, IncidentMapper.class),
        mapperOf(sqlSessionFactory, JobMapper.class),
        mapperOf(sqlSessionFactory, JobMetricsBatchMapper.class),
        mapperOf(sqlSessionFactory, MappingRuleMapper.class),
        mapperOf(sqlSessionFactory, MessageSubscriptionMapper.class),
        mapperOf(sqlSessionFactory, PersistentWebSessionMapper.class),
        mapperOf(sqlSessionFactory, ProcessDefinitionMapper.class),
        mapperOf(sqlSessionFactory, ProcessInstanceMapper.class),
        mapperOf(sqlSessionFactory, PurgeMapper.class),
        mapperOf(sqlSessionFactory, RoleMapper.class),
        mapperOf(sqlSessionFactory, SequenceFlowMapper.class),
        mapperOf(sqlSessionFactory, TableMetricsMapper.class),
        mapperOf(sqlSessionFactory, TenantMapper.class),
        mapperOf(sqlSessionFactory, UsageMetricMapper.class),
        mapperOf(sqlSessionFactory, UsageMetricTUMapper.class),
        mapperOf(sqlSessionFactory, UserMapper.class),
        mapperOf(sqlSessionFactory, UserTaskMapper.class),
        mapperOf(sqlSessionFactory, VariableMapper.class));
  }

  private static <T> T mapperOf(final SqlSessionFactory sqlSessionFactory, final Class<T> type) {
    final MapperFactoryBean<T> factoryBean = new MapperFactoryBean<>(type);
    factoryBean.setSqlSessionFactory(sqlSessionFactory);
    try {
      factoryBean.afterPropertiesSet();
      return factoryBean.getObject();
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to initialize MyBatis mapper " + type.getName(), e);
    }
  }

  private static RdbmsMapperBundle defaultBundle(final Map<String, RdbmsMapperBundle> bundles) {
    final var bundle = bundles.get(DEFAULT_PHYSICAL_TENANT_ID);
    if (bundle == null) {
      throw new IllegalStateException(
          "No RdbmsMapperBundle configured for physical tenant " + DEFAULT_PHYSICAL_TENANT_ID);
    }
    return bundle;
  }
}
