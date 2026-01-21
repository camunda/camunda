/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Engine;
import io.camunda.configuration.Rdbms;
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
import io.camunda.db.rdbms.sql.GroupMapper;
import io.camunda.db.rdbms.sql.HistoryDeletionMapper;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.JobMapper;
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
import java.util.HashMap;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

// @Import(DataSourceAutoConfiguration.class)
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
      final Map<String, DataSource> engineDataSources,
      final VendorDatabaseProperties vendorDatabaseProperties,
      @Value("${camunda.data.secondary-storage.rdbms.prefix:}") final String prefix,
      final Camunda camunda) {
    final String trimmedPrefix = StringUtils.trimToEmpty(prefix);
    LOGGER.info(
        "Initializing Liquibase for RDBMS with global table trimmedPrefix '{}'.", trimmedPrefix);

    final var moduleConfig = new LiquibaseSchemaManager();
    moduleConfig.setEngineDataSources(engineDataSources);
    moduleConfig.setDataSource(dataSource);
    moduleConfig.setDatabaseChangeLogTable(trimmedPrefix + "DATABASECHANGELOG");
    moduleConfig.setDatabaseChangeLogLockTable(trimmedPrefix + "DATABASECHANGELOGLOCK");
    moduleConfig.setParameters(
        Map.of(
            "prefix",
            trimmedPrefix,
            "userCharColumnSize",
            Integer.toString(vendorDatabaseProperties.userCharColumnSize())));
    // changelog file located in src/main/resources directly in the module
    moduleConfig.setChangeLog("db/changelog/rdbms-exporter/changelog-master.xml");
    /*
    final Map<String, DataSource> engineDataSources = new HashMap<>();
    for (int i = 0; i < camunda.getEngines().size(); i++) {
      final Engine engine = camunda.getEngines().get(i);
      String engineName = engine.getName();
      if (engineName == null || engineName.isBlank()) {
        engineName = String.valueOf(i);
      }
      final var rdbms = engine.getData().getSecondaryStorage().getRdbms();
      final var engineDataSource = createDataSource(rdbms);
      engineDataSources.put(engineName, engineDataSource);
    }
    moduleConfig.setTenantDataSources(engineDataSource);
     */
    return moduleConfig;
  }

  @Bean
  public Map<String, DataSource> engineDataSources(final Camunda camunda) {
    final Map<String, DataSource> engineDataSources = new HashMap<>();
    for (int i = 0; i < camunda.getEngines().size(); i++) {
      final Engine engine = camunda.getEngines().get(i);
      String engineName = engine.getName();
      if (engineName == null || engineName.isBlank()) {
        engineName = String.valueOf(i);
      }
      final var rdbms = engine.getData().getSecondaryStorage().getRdbms();
      final var engineDataSource = createDataSource(rdbms);
      engineDataSources.put(engineName, engineDataSource);
    }
    return engineDataSources;
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
  public Map<String, SqlSessionFactory> engineSqlSessionFactories(
      final Camunda camunda, final DatabaseIdProvider databaseIdProvider) throws Exception {
    final Map<String, SqlSessionFactory> engineSqlSessionFactories = new HashMap<>();

    for (int i = 0; i < camunda.getEngines().size(); i++) {
      final Engine engine = camunda.getEngines().get(i);
      String engineName = engine.getName();
      if (engineName == null || engineName.isBlank()) {
        engineName = String.valueOf(i);
      }
      final var rdbms = engine.getData().getSecondaryStorage().getRdbms();
      final var engineDataSource = createDataSource(rdbms);

      final var databaseId = databaseIdProvider.getDatabaseId(engineDataSource);
      final var databaseProperties = VendorDatabasePropertiesLoader.load(databaseId);
      final var prefix = rdbms.getPrefix();

      final var sqlSessionFactory =
          createSqlSessionFactory(
              engineDataSource,
              databaseIdProvider,
              databaseProperties,
              prefix != null ? prefix : "");
      engineSqlSessionFactories.put(engineName, sqlSessionFactory);
    }

    return engineSqlSessionFactories;
  }

  @Bean
  @Primary
  public SqlSessionFactory sqlSessionFactory(
      final DataSource dataSource,
      final DatabaseIdProvider databaseIdProvider,
      final VendorDatabaseProperties databaseProperties,
      @Value("${camunda.data.secondary-storage.rdbms.prefix:}") final String prefix)
      throws Exception {
    return createSqlSessionFactory(dataSource, databaseIdProvider, databaseProperties, prefix);
  }

  @Bean
  public DataSource dataSource(final Camunda camunda) {
    return createDataSource(camunda.getData().getSecondaryStorage().getRdbms());
  }

  private SqlSessionFactory createSqlSessionFactory(
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
  public AuditLogMapper auditLogMapper(
      final SqlSessionFactory sqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        AuditLogMapper.class, sqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public AuthorizationMapper authorizationMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        AuthorizationMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public Map<String, SqlSessionTemplate> engineSqlSessionTemplates(
      final Map<String, SqlSessionFactory> engineSqlSessionFactories) {
    final Map<String, SqlSessionTemplate> templates = new HashMap<>();
    for (final var entry : engineSqlSessionFactories.entrySet()) {
      templates.put(entry.getKey(), new SqlSessionTemplate(entry.getValue()));
    }
    return templates;
  }

  @Bean
  public DecisionDefinitionMapper decisionDefinitionMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        DecisionDefinitionMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public DecisionInstanceMapper decisionInstanceMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        DecisionInstanceMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public DecisionRequirementsMapper decisionRequirementsMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        DecisionRequirementsMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public FlowNodeInstanceMapper flowNodeInstanceMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        FlowNodeInstanceMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public GroupMapper groupInstanceMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        GroupMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public IncidentMapper incidentMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        IncidentMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public ProcessInstanceMapper processInstanceMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        ProcessInstanceMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public ProcessDefinitionMapper processDeploymentMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        ProcessDefinitionMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public TenantMapper tenantMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        TenantMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public VariableMapper variableMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        VariableMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public ClusterVariableMapper clusterVariableMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        ClusterVariableMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public RoleMapper roleMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        RoleMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public UserMapper userMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        UserMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public UserTaskMapper userTaskMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        UserTaskMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public FormMapper formMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        FormMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public MappingRuleMapper mappingMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        MappingRuleMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public ExporterPositionMapper exporterPosition(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        ExporterPositionMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public PurgeMapper purgeMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        PurgeMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public BatchOperationMapper batchOperationMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        BatchOperationMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public JobMapper jobMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        JobMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public SequenceFlowMapper sequenceFlowMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        SequenceFlowMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public UsageMetricMapper usageMetricMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        UsageMetricMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public UsageMetricTUMapper usageMetricTUMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        UsageMetricTUMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public MessageSubscriptionMapper messageSubscriptionMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        MessageSubscriptionMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public CorrelatedMessageSubscriptionMapper correlatedMessageSubscriptionMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        CorrelatedMessageSubscriptionMapper.class,
        defaultSqlSessionFactory,
        engineSqlSessionTemplates);
  }

  @Bean
  public TableMetricsMapper tableMetricsMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        TableMetricsMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public HistoryDeletionMapper historyDeletionMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        HistoryDeletionMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  @Bean
  public PersistentWebSessionMapper persistentWebSessionMapper(
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    return createEngineAwareMapper(
        PersistentWebSessionMapper.class, defaultSqlSessionFactory, engineSqlSessionTemplates);
  }

  private <T> T createEngineAwareMapper(
      final Class<T> mapperClass,
      final SqlSessionFactory defaultSqlSessionFactory,
      final Map<String, SqlSessionTemplate> engineSqlSessionTemplates) {
    final SqlSessionTemplate defaultTemplate = new SqlSessionTemplate(defaultSqlSessionFactory);
    final T defaultMapper = defaultTemplate.getMapper(mapperClass);

    final Map<String, T> engineMappers = new HashMap<>();
    for (final var entry : engineSqlSessionTemplates.entrySet()) {
      engineMappers.put(entry.getKey(), entry.getValue().getMapper(mapperClass));
    }

    return MultiEngineMapperProxy.create(mapperClass, defaultMapper, engineMappers);
  }

  private DataSource createDataSource(final Rdbms rdbms) {
    final var config = new HikariConfig();
    config.setJdbcUrl(rdbms.getUrl());
    config.setUsername(rdbms.getUsername());
    config.setPassword(rdbms.getPassword());

    final var connectionPool = rdbms.getConnectionPool();
    config.setMaximumPoolSize(connectionPool.getMaximumPoolSize());
    config.setMinimumIdle(connectionPool.getMinimumIdle());
    config.setIdleTimeout(connectionPool.getIdleTimeout());
    config.setMaxLifetime(connectionPool.getMaxLifetime());
    config.setConnectionTimeout(connectionPool.getConnectionTimeout());

    return new HikariDataSource(config);
  }
}
