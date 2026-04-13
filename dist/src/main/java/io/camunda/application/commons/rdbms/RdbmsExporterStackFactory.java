/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.configuration.Rdbms;
import io.camunda.db.rdbms.LiquibaseSchemaManager;
import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.service.AuditLogDbReader;
import io.camunda.db.rdbms.read.service.AuthorizationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationItemDbReader;
import io.camunda.db.rdbms.read.service.ClusterVariableDbReader;
import io.camunda.db.rdbms.read.service.CorrelatedMessageSubscriptionDbReader;
import io.camunda.db.rdbms.read.service.DecisionDefinitionDbReader;
import io.camunda.db.rdbms.read.service.DecisionInstanceDbReader;
import io.camunda.db.rdbms.read.service.DecisionRequirementsDbReader;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceDbReader;
import io.camunda.db.rdbms.read.service.FormDbReader;
import io.camunda.db.rdbms.read.service.GlobalListenerDbReader;
import io.camunda.db.rdbms.read.service.GroupDbReader;
import io.camunda.db.rdbms.read.service.GroupMemberDbReader;
import io.camunda.db.rdbms.read.service.HistoryDeletionDbReader;
import io.camunda.db.rdbms.read.service.IncidentDbReader;
import io.camunda.db.rdbms.read.service.IncidentProcessInstanceStatisticsByDefinitionDbReader;
import io.camunda.db.rdbms.read.service.IncidentProcessInstanceStatisticsByErrorDbReader;
import io.camunda.db.rdbms.read.service.JobDbReader;
import io.camunda.db.rdbms.read.service.JobMetricsBatchDbReader;
import io.camunda.db.rdbms.read.service.MappingRuleDbReader;
import io.camunda.db.rdbms.read.service.MessageSubscriptionDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionInstanceStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionInstanceVersionStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionMessageSubscriptionStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.read.service.RoleDbReader;
import io.camunda.db.rdbms.read.service.RoleMemberDbReader;
import io.camunda.db.rdbms.read.service.SequenceFlowDbReader;
import io.camunda.db.rdbms.read.service.TenantDbReader;
import io.camunda.db.rdbms.read.service.TenantMemberDbReader;
import io.camunda.db.rdbms.read.service.UsageMetricTUDbReader;
import io.camunda.db.rdbms.read.service.UsageMetricsDbReader;
import io.camunda.db.rdbms.read.service.UserDbReader;
import io.camunda.db.rdbms.read.service.UserTaskDbReader;
import io.camunda.db.rdbms.read.service.VariableDbReader;
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
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.sql.RoleMapper;
import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.db.rdbms.sql.TenantMapper;
import io.camunda.db.rdbms.sql.UsageMetricMapper;
import io.camunda.db.rdbms.sql.UsageMetricTUMapper;
import io.camunda.db.rdbms.sql.UserMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Factory that builds a complete, independent RDBMS exporter stack (DataSource, MyBatis
 * SqlSessionFactory, all mappers, readers, writers, Liquibase schema manager) from a {@link Rdbms}
 * configuration entry. Used when multiple independent RDBMS exporters are configured.
 */
public final class RdbmsExporterStackFactory {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsExporterStackFactory.class);

  private RdbmsExporterStackFactory() {}

  /**
   * Builds a complete RDBMS exporter stack from the given configuration.
   *
   * @param config the Rdbms configuration entry (contains URL, credentials, etc.)
   * @param meterRegistry the meter registry for metrics
   * @return an {@link RdbmsExporterStack} containing all components needed for an exporter
   */
  public static RdbmsExporterStack build(final Rdbms config, final MeterRegistry meterRegistry)
      throws Exception {
    LOG.info("Building independent RDBMS exporter stack for URL: {}", config.getUrl());

    // 1. Create DataSource
    final DataSource dataSource =
        DataSourceBuilder.create()
            .url(config.getUrl())
            .username(config.getUsername())
            .password(config.getPassword())
            .build();

    // 2. Detect vendor and load properties
    final var dbIdProvider = new RdbmsDatabaseIdProvider("");
    final String dbId = dbIdProvider.getDatabaseId(dataSource);
    LOG.info("Detected databaseId for exporter stack: {}", dbId);
    final VendorDatabaseProperties vendorProps = VendorDatabasePropertiesLoader.load(dbId);

    // 3. Create SqlSessionFactory
    final SqlSessionFactory sqlSessionFactory =
        createSqlSessionFactory(dataSource, dbIdProvider, vendorProps, config.getPrefix());

    // 4. Register all mapper classes on the SqlSessionFactory
    final var mybatisConfig = sqlSessionFactory.getConfiguration();
    mybatisConfig.addMapper(AuditLogMapper.class);
    mybatisConfig.addMapper(AuthorizationMapper.class);
    mybatisConfig.addMapper(BatchOperationMapper.class);
    mybatisConfig.addMapper(ClusterVariableMapper.class);
    mybatisConfig.addMapper(CorrelatedMessageSubscriptionMapper.class);
    mybatisConfig.addMapper(DecisionDefinitionMapper.class);
    mybatisConfig.addMapper(DecisionInstanceMapper.class);
    mybatisConfig.addMapper(DecisionRequirementsMapper.class);
    mybatisConfig.addMapper(ExporterPositionMapper.class);
    mybatisConfig.addMapper(FlowNodeInstanceMapper.class);
    mybatisConfig.addMapper(FormMapper.class);
    mybatisConfig.addMapper(GlobalListenerMapper.class);
    mybatisConfig.addMapper(GroupMapper.class);
    mybatisConfig.addMapper(HistoryDeletionMapper.class);
    mybatisConfig.addMapper(IncidentMapper.class);
    mybatisConfig.addMapper(JobMapper.class);
    mybatisConfig.addMapper(JobMetricsBatchMapper.class);
    mybatisConfig.addMapper(MappingRuleMapper.class);
    mybatisConfig.addMapper(MessageSubscriptionMapper.class);
    mybatisConfig.addMapper(ProcessDefinitionMapper.class);
    mybatisConfig.addMapper(ProcessInstanceMapper.class);
    mybatisConfig.addMapper(PurgeMapper.class);
    mybatisConfig.addMapper(RoleMapper.class);
    mybatisConfig.addMapper(SequenceFlowMapper.class);
    mybatisConfig.addMapper(TenantMapper.class);
    mybatisConfig.addMapper(UsageMetricMapper.class);
    mybatisConfig.addMapper(UsageMetricTUMapper.class);
    mybatisConfig.addMapper(UserMapper.class);
    mybatisConfig.addMapper(UserTaskMapper.class);
    mybatisConfig.addMapper(VariableMapper.class);

    // 5. Create SqlSessionTemplate for mapper proxies
    final SqlSessionTemplate session = new SqlSessionTemplate(sqlSessionFactory);

    // 6. Create mapper instances
    final AuditLogMapper auditLogMapper = session.getMapper(AuditLogMapper.class);
    final AuthorizationMapper authorizationMapper = session.getMapper(AuthorizationMapper.class);
    final BatchOperationMapper batchOperationMapper = session.getMapper(BatchOperationMapper.class);
    final ClusterVariableMapper clusterVariableMapper =
        session.getMapper(ClusterVariableMapper.class);
    final CorrelatedMessageSubscriptionMapper correlatedMessageSubscriptionMapper =
        session.getMapper(CorrelatedMessageSubscriptionMapper.class);
    final DecisionDefinitionMapper decisionDefinitionMapper =
        session.getMapper(DecisionDefinitionMapper.class);
    final DecisionInstanceMapper decisionInstanceMapper =
        session.getMapper(DecisionInstanceMapper.class);
    final DecisionRequirementsMapper decisionRequirementsMapper =
        session.getMapper(DecisionRequirementsMapper.class);
    final ExporterPositionMapper exporterPositionMapper =
        session.getMapper(ExporterPositionMapper.class);
    final FlowNodeInstanceMapper flowNodeInstanceMapper =
        session.getMapper(FlowNodeInstanceMapper.class);
    final FormMapper formMapper = session.getMapper(FormMapper.class);
    final GlobalListenerMapper globalListenerMapper = session.getMapper(GlobalListenerMapper.class);
    final GroupMapper groupMapper = session.getMapper(GroupMapper.class);
    final HistoryDeletionMapper historyDeletionMapper =
        session.getMapper(HistoryDeletionMapper.class);
    final IncidentMapper incidentMapper = session.getMapper(IncidentMapper.class);
    final JobMapper jobMapper = session.getMapper(JobMapper.class);
    final JobMetricsBatchMapper jobMetricsBatchMapper =
        session.getMapper(JobMetricsBatchMapper.class);
    final MappingRuleMapper mappingRuleMapper = session.getMapper(MappingRuleMapper.class);
    final MessageSubscriptionMapper messageSubscriptionMapper =
        session.getMapper(MessageSubscriptionMapper.class);
    final ProcessDefinitionMapper processDefinitionMapper =
        session.getMapper(ProcessDefinitionMapper.class);
    final ProcessInstanceMapper processInstanceMapper =
        session.getMapper(ProcessInstanceMapper.class);
    final PurgeMapper purgeMapper = session.getMapper(PurgeMapper.class);
    final RoleMapper roleMapper = session.getMapper(RoleMapper.class);
    final SequenceFlowMapper sequenceFlowMapper = session.getMapper(SequenceFlowMapper.class);
    final TenantMapper tenantMapper = session.getMapper(TenantMapper.class);
    final UsageMetricMapper usageMetricMapper = session.getMapper(UsageMetricMapper.class);
    final UsageMetricTUMapper usageMetricTUMapper = session.getMapper(UsageMetricTUMapper.class);
    final UserMapper userMapper = session.getMapper(UserMapper.class);
    final UserTaskMapper userTaskMapper = session.getMapper(UserTaskMapper.class);
    final VariableMapper variableMapper = session.getMapper(VariableMapper.class);

    // 7. Create reader config
    final RdbmsReaderConfig readerConfig = config.getQuery().toReaderConfig();

    // 8. Create readers
    final var variableReader = new VariableDbReader(variableMapper, readerConfig);
    final var clusterVariableReader =
        new ClusterVariableDbReader(clusterVariableMapper, readerConfig);
    final var authorizationReader = new AuthorizationDbReader(authorizationMapper, readerConfig);
    final var auditLogReader = new AuditLogDbReader(auditLogMapper, readerConfig);
    final var decisionDefinitionReader =
        new DecisionDefinitionDbReader(decisionDefinitionMapper, readerConfig);
    final var decisionInstanceReader =
        new DecisionInstanceDbReader(decisionInstanceMapper, readerConfig);
    final var decisionRequirementsReader =
        new DecisionRequirementsDbReader(decisionRequirementsMapper, readerConfig);
    final var flowNodeInstanceReader =
        new FlowNodeInstanceDbReader(flowNodeInstanceMapper, readerConfig);
    final var groupReader = new GroupDbReader(groupMapper, readerConfig);
    final var groupMemberReader = new GroupMemberDbReader(groupMapper, readerConfig);
    final var incidentReader = new IncidentDbReader(incidentMapper, readerConfig);
    final var processDefinitionReader =
        new ProcessDefinitionDbReader(processDefinitionMapper, readerConfig);
    final var processInstanceReader =
        new ProcessInstanceDbReader(processInstanceMapper, readerConfig);
    final var roleReader = new RoleDbReader(roleMapper, readerConfig);
    final var roleMemberReader = new RoleMemberDbReader(roleMapper, readerConfig);
    final var tenantReader = new TenantDbReader(tenantMapper, readerConfig);
    final var tenantMemberReader = new TenantMemberDbReader(tenantMapper, readerConfig);
    final var userReader = new UserDbReader(userMapper, readerConfig);
    final var userTaskReader = new UserTaskDbReader(userTaskMapper, readerConfig);
    final var formReader = new FormDbReader(formMapper, readerConfig);
    final var mappingReader = new MappingRuleDbReader(mappingRuleMapper, readerConfig);
    final var batchOperationReader = new BatchOperationDbReader(batchOperationMapper, readerConfig);
    final var sequenceFlowReader = new SequenceFlowDbReader(sequenceFlowMapper, readerConfig);
    final var batchOperationItemReader =
        new BatchOperationItemDbReader(batchOperationMapper, readerConfig);
    final var jobReader = new JobDbReader(jobMapper, readerConfig);
    final var jobMetricsBatchReader =
        new JobMetricsBatchDbReader(jobMetricsBatchMapper, readerConfig);
    final var usageMetricReader = new UsageMetricsDbReader(usageMetricMapper);
    final var usageMetricTUReader = new UsageMetricTUDbReader(usageMetricTUMapper);
    final var messageSubscriptionReader =
        new MessageSubscriptionDbReader(messageSubscriptionMapper, readerConfig);
    final var processDefinitionMessageSubscriptionStatisticsReader =
        new ProcessDefinitionMessageSubscriptionStatisticsDbReader(
            messageSubscriptionMapper, readerConfig);
    final var correlatedMessageSubscriptionReader =
        new CorrelatedMessageSubscriptionDbReader(
            correlatedMessageSubscriptionMapper, readerConfig);
    final var processDefinitionInstanceStatisticsReader =
        new ProcessDefinitionInstanceStatisticsDbReader(processDefinitionMapper, readerConfig);
    final var processDefinitionInstanceVersionStatisticsReader =
        new ProcessDefinitionInstanceVersionStatisticsDbReader(
            processDefinitionMapper, readerConfig);
    final var historyDeletionReader = new HistoryDeletionDbReader(historyDeletionMapper);
    final var incidentProcessInstanceStatisticsByErrorReader =
        new IncidentProcessInstanceStatisticsByErrorDbReader(incidentMapper, readerConfig);
    final var incidentProcessInstanceStatisticsByDefinitionReader =
        new IncidentProcessInstanceStatisticsByDefinitionDbReader(incidentMapper, readerConfig);
    final var globalListenerReader = new GlobalListenerDbReader(globalListenerMapper, readerConfig);

    // 9. Create RdbmsWriterFactory
    final var writerFactory =
        new RdbmsWriterFactory(
            sqlSessionFactory,
            exporterPositionMapper,
            vendorProps,
            auditLogMapper,
            decisionInstanceMapper,
            decisionDefinitionMapper,
            decisionRequirementsMapper,
            flowNodeInstanceMapper,
            incidentMapper,
            processInstanceMapper,
            processDefinitionMapper,
            purgeMapper,
            userTaskMapper,
            variableMapper,
            meterRegistry,
            batchOperationReader,
            jobMapper,
            jobMetricsBatchMapper,
            sequenceFlowMapper,
            usageMetricMapper,
            usageMetricTUMapper,
            batchOperationMapper,
            messageSubscriptionMapper,
            correlatedMessageSubscriptionMapper,
            clusterVariableMapper,
            historyDeletionMapper);

    // 10. Create RdbmsService
    final var rdbmsService =
        new RdbmsService(
            writerFactory,
            auditLogReader,
            authorizationReader,
            decisionDefinitionReader,
            decisionInstanceReader,
            decisionRequirementsReader,
            flowNodeInstanceReader,
            groupReader,
            groupMemberReader,
            incidentReader,
            processDefinitionReader,
            processInstanceReader,
            variableReader,
            clusterVariableReader,
            roleReader,
            roleMemberReader,
            tenantReader,
            tenantMemberReader,
            userReader,
            userTaskReader,
            formReader,
            mappingReader,
            batchOperationReader,
            sequenceFlowReader,
            batchOperationItemReader,
            jobReader,
            jobMetricsBatchReader,
            usageMetricReader,
            usageMetricTUReader,
            messageSubscriptionReader,
            processDefinitionMessageSubscriptionStatisticsReader,
            correlatedMessageSubscriptionReader,
            processDefinitionInstanceStatisticsReader,
            processDefinitionInstanceVersionStatisticsReader,
            historyDeletionReader,
            incidentProcessInstanceStatisticsByErrorReader,
            incidentProcessInstanceStatisticsByDefinitionReader,
            globalListenerReader);

    // 11. Create LiquibaseSchemaManager (if auto-DDL is enabled)
    final RdbmsSchemaManager schemaManager;
    if (Boolean.TRUE.equals(config.getAutoDdl())) {
      schemaManager = createSchemaManager(dataSource, vendorProps, config);
    } else {
      schemaManager = new io.camunda.db.rdbms.NoopSchemaManager();
    }

    return new RdbmsExporterStack(rdbmsService, schemaManager, vendorProps);
  }

  private static SqlSessionFactory createSqlSessionFactory(
      final DataSource dataSource,
      final RdbmsDatabaseIdProvider dbIdProvider,
      final VendorDatabaseProperties vendorProps,
      final String prefix)
      throws Exception {
    final var configuration = new org.apache.ibatis.session.Configuration();
    configuration.setJdbcTypeForNull(JdbcType.NULL);
    configuration.getTypeHandlerRegistry().register(OffsetDateTimeTypeHandler.class);

    final SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setConfiguration(configuration);
    factoryBean.setDataSource(dataSource);
    factoryBean.setDatabaseIdProvider(dbIdProvider);
    factoryBean.addMapperLocations(
        new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));

    final Properties p = new Properties();
    p.put("prefix", StringUtils.trimToEmpty(prefix));
    p.putAll(vendorProps.properties());
    factoryBean.setConfigurationProperties(p);

    return factoryBean.getObject();
  }

  private static RdbmsSchemaManager createSchemaManager(
      final DataSource dataSource, final VendorDatabaseProperties vendorProps, final Rdbms config) {
    final String trimmedPrefix = StringUtils.trimToEmpty(config.getPrefix());
    LOG.info("Initializing Liquibase for RDBMS exporter with table prefix '{}'.", trimmedPrefix);

    final var schemaManager = new LiquibaseSchemaManager();
    schemaManager.setDataSource(dataSource);
    schemaManager.setDatabaseChangeLogTable(trimmedPrefix + "DATABASECHANGELOG");
    schemaManager.setDatabaseChangeLogLockTable(trimmedPrefix + "DATABASECHANGELOGLOCK");
    schemaManager.setParameters(
        Map.of(
            "prefix",
            trimmedPrefix,
            "userCharColumnSize",
            Integer.toString(vendorProps.userCharColumnSize()),
            "errorMessageSize",
            Integer.toString(vendorProps.errorMessageSize()),
            "treePathSize",
            Integer.toString(vendorProps.treePathSize())));
    schemaManager.setChangeLog("db/changelog/rdbms-exporter/changelog-master.xml");
    schemaManager.setDdlLockWaitTimeout(config.getDdlLockWaitTimeout());
    return schemaManager;
  }
}
