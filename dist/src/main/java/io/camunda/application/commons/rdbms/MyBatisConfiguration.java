/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static io.camunda.configuration.physicaltenants.PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
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
import io.camunda.db.rdbms.sql.ReplicationStatusMapper;
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
import io.camunda.zeebe.util.VersionUtil;
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
import org.mybatis.spring.SqlSessionTemplate;
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
    // Inject the current application version for schema upgrade-path validation.
    // When the version is not a valid semantic version (e.g. during local development),
    // the version check is skipped inside LiquibaseSchemaManager.
    moduleConfig.setApplicationVersion(VersionUtil.getVersion());

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
      final PhysicalTenantResolver physicalTenantResolver,
      final RdbmsDatabaseIdProvider databaseIdProvider)
      throws IOException {
    return RdbmsDataSources.of(
        physicalTenantResolver.mapValues(
            camunda -> camunda.getData().getSecondaryStorage().getRdbms()),
        databaseIdProvider);
  }

  // The following 2 beans expose the default physical tenant's DataSource and
  // VendorDatabaseProperties so that downstream consumers (sqlSessionFactory,
  // rdbmsExporterLiquibase, rdbmsWriterFactory, rdbmsExporterFactory)
  // can keep injecting them as singletons. They will be removed in future PRs.

  @Bean
  public DataSource dataSource(final RdbmsDataSources rdbmsDataSources) {
    return rdbmsDataSources.dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public VendorDatabaseProperties databaseProperties(final RdbmsDataSources rdbmsDataSources) {
    return rdbmsDataSources.vendorPropertiesFor(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public Map<String, DataSource> dataSources(
      final DataSource dataSource, final RdbmsDataSources rdbmsDataSources) {
    final var map = new LinkedHashMap<String, DataSource>();
    for (final var physicalTenantId : rdbmsDataSources.physicalTenantIds()) {
      map.put(
          physicalTenantId,
          DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)
              ? dataSource
              : rdbmsDataSources.dataSourceFor(physicalTenantId));
    }
    return Map.copyOf(map);
  }

  @Bean
  public SqlSessionFactory sqlSessionFactory(
      final Map<String, SqlSessionFactory> sqlSessionFactories) {
    return sqlSessionFactories.get(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public Map<String, SqlSessionTemplate> sqlSessionTemplates(
      final Map<String, SqlSessionFactory> sqlSessionFactories) {
    final var templates = new LinkedHashMap<String, SqlSessionTemplate>();
    for (final var entry : sqlSessionFactories.entrySet()) {
      templates.put(entry.getKey(), new SqlSessionTemplate(entry.getValue()));
    }
    return Map.copyOf(templates);
  }

  @Bean
  public SqlSessionTemplate sqlSessionTemplate(
      final Map<String, SqlSessionTemplate> sqlSessionTemplates) {
    return sqlSessionTemplates.get(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public Map<String, SqlSessionFactory> sqlSessionFactories(
      final Map<String, DataSource> dataSources,
      final RdbmsDataSources rdbmsDataSources,
      final PhysicalTenantResolver physicalTenantResolver,
      final DatabaseIdProvider databaseIdProvider)
      throws Exception {
    final var factories = new LinkedHashMap<String, SqlSessionFactory>();
    for (final var entry : dataSources.entrySet()) {
      final var physicalTenantId = entry.getKey();
      final var prefix =
          physicalTenantResolver
              .forPhysicalTenant(physicalTenantId)
              .getData()
              .getSecondaryStorage()
              .getRdbms()
              .getPrefix();
      factories.put(
          physicalTenantId,
          buildSqlSessionFactory(
              entry.getValue(),
              databaseIdProvider,
              rdbmsDataSources.vendorPropertiesFor(physicalTenantId),
              prefix));
    }
    return Map.copyOf(factories);
  }

  @Bean
  public Map<String, RdbmsMapperBundle> rdbmsMapperBundles(
      final Map<String, SqlSessionFactory> sqlSessionFactories,
      final Map<String, SqlSessionTemplate> sqlSessionTemplates,
      final RdbmsDataSources rdbmsDataSources) {
    final var bundles = new LinkedHashMap<String, RdbmsMapperBundle>();
    for (final var entry : sqlSessionFactories.entrySet()) {
      final var physicalTenantId = entry.getKey();
      final var factory = entry.getValue();
      final var session = sqlSessionTemplates.get(physicalTenantId);
      bundles.put(
          physicalTenantId,
          RdbmsMapperBundle.from(
              factory, session, rdbmsDataSources.vendorPropertiesFor(physicalTenantId)));
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

  @Bean
  public AuthorizationMapper authorizationMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(AuthorizationMapper.class);
  }

  @Bean
  public AuditLogMapper auditLogMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(AuditLogMapper.class);
  }

  @Bean
  public DecisionDefinitionMapper decisionDefinitionMapper(
      final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(DecisionDefinitionMapper.class);
  }

  @Bean
  public DecisionInstanceMapper decisionInstanceMapper(
      final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(DecisionInstanceMapper.class);
  }

  @Bean
  public DecisionRequirementsMapper decisionRequirementsMapper(
      final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(DecisionRequirementsMapper.class);
  }

  @Bean
  public FlowNodeInstanceMapper flowNodeInstanceMapper(
      final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(FlowNodeInstanceMapper.class);
  }

  @Bean
  public GroupMapper groupInstanceMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(GroupMapper.class);
  }

  @Bean
  public IncidentMapper incidentMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(IncidentMapper.class);
  }

  @Bean
  public ProcessInstanceMapper processInstanceMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(ProcessInstanceMapper.class);
  }

  @Bean
  public ProcessDefinitionMapper processDeploymentMapper(
      final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(ProcessDefinitionMapper.class);
  }

  @Bean
  public TenantMapper tenantMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(TenantMapper.class);
  }

  @Bean
  public VariableMapper variableMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(VariableMapper.class);
  }

  @Bean
  public ClusterVariableMapper clusterVariableMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(ClusterVariableMapper.class);
  }

  @Bean
  public JobMetricsBatchMapper jobMetricsBatchMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(JobMetricsBatchMapper.class);
  }

  @Bean
  public RoleMapper roleMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(RoleMapper.class);
  }

  @Bean
  public UserMapper userMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(UserMapper.class);
  }

  @Bean
  public UserTaskMapper userTaskMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(UserTaskMapper.class);
  }

  @Bean
  public FormMapper formMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(FormMapper.class);
  }

  @Bean
  public MappingRuleMapper mappingMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(MappingRuleMapper.class);
  }

  @Bean
  public ExporterPositionMapper exporterPosition(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(ExporterPositionMapper.class);
  }

  @Bean
  public PurgeMapper purgeMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(PurgeMapper.class);
  }

  @Bean
  public BatchOperationMapper batchOperationMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(BatchOperationMapper.class);
  }

  @Bean
  public JobMapper jobMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(JobMapper.class);
  }

  @Bean
  public SequenceFlowMapper sequenceFlowMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(SequenceFlowMapper.class);
  }

  @Bean
  public UsageMetricMapper usageMetricMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(UsageMetricMapper.class);
  }

  @Bean
  public UsageMetricTUMapper usageMetricTUMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(UsageMetricTUMapper.class);
  }

  @Bean
  MessageSubscriptionMapper messageSubscriptionMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(MessageSubscriptionMapper.class);
  }

  @Bean
  CorrelatedMessageSubscriptionMapper correlatedMessageSubscriptionMapper(
      final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(CorrelatedMessageSubscriptionMapper.class);
  }

  @Bean
  TableMetricsMapper tableMetricsMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(TableMetricsMapper.class);
  }

  @Bean
  HistoryDeletionMapper historyDeletionMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(HistoryDeletionMapper.class);
  }

  @Bean
  ReplicationStatusMapper replicationStatusMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(ReplicationStatusMapper.class);
  }

  @Bean
  PersistentWebSessionMapper persistentWebSessionMapper(
      final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(PersistentWebSessionMapper.class);
  }

  @Bean
  public GlobalListenerMapper globalListenerMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(GlobalListenerMapper.class);
  }

  @Bean
  public DeployedResourceMapper resourceMapper(final SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(DeployedResourceMapper.class);
  }
}
