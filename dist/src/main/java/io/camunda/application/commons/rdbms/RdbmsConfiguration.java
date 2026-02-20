/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
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
import io.camunda.db.rdbms.read.service.PersistentWebSessionDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionInstanceStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionInstanceVersionStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionMessageSubscriptionStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceStatisticsDbReader;
import io.camunda.db.rdbms.read.service.RdbmsTableRowCountMetrics;
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
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import io.camunda.db.rdbms.write.service.PersistentWebSessionWriter;
import io.camunda.search.clients.reader.ProcessDefinitionMessageSubscriptionStatisticsReader;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnSecondaryStorageType(SecondaryStorageType.rdbms)
@Import(MyBatisConfiguration.class)
public class RdbmsConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsConfiguration.class);

  @Bean
  public RdbmsReaderConfig rdbmsReaderConfig(final Camunda configuration) {
    return configuration.getData().getSecondaryStorage().getRdbms().getQuery().toReaderConfig();
  }

  @Bean
  public VariableDbReader variableRdbmsReader(
      final VariableMapper variableMapper, final RdbmsReaderConfig readerConfig) {
    return new VariableDbReader(variableMapper, readerConfig);
  }

  @Bean
  public ClusterVariableDbReader clusterVariableRdbmsReader(
      final ClusterVariableMapper clusterVariableMapper, final RdbmsReaderConfig readerConfig) {
    return new ClusterVariableDbReader(clusterVariableMapper, readerConfig);
  }

  @Bean
  public AuthorizationDbReader authorizationReader(
      final AuthorizationMapper authorizationMapper, final RdbmsReaderConfig readerConfig) {
    return new AuthorizationDbReader(authorizationMapper, readerConfig);
  }

  @Bean
  public AuditLogDbReader auditLogReader(
      final AuditLogMapper auditLogMapper, final RdbmsReaderConfig readerConfig) {
    return new AuditLogDbReader(auditLogMapper, readerConfig);
  }

  @Bean
  public DecisionDefinitionDbReader decisionDefinitionReader(
      final DecisionDefinitionMapper decisionDefinitionMapper,
      final RdbmsReaderConfig readerConfig) {
    return new DecisionDefinitionDbReader(decisionDefinitionMapper, readerConfig);
  }

  @Bean
  public DecisionInstanceDbReader decisionInstanceReader(
      final DecisionInstanceMapper decisionInstanceMapper, final RdbmsReaderConfig readerConfig) {
    return new DecisionInstanceDbReader(decisionInstanceMapper, readerConfig);
  }

  @Bean
  public DecisionRequirementsDbReader decisionRequirementsReader(
      final DecisionRequirementsMapper decisionRequirementsMapper,
      final RdbmsReaderConfig readerConfig) {
    return new DecisionRequirementsDbReader(decisionRequirementsMapper, readerConfig);
  }

  @Bean
  public FlowNodeInstanceDbReader flowNodeInstanceReader(
      final FlowNodeInstanceMapper flowNodeInstanceMapper, final RdbmsReaderConfig readerConfig) {
    return new FlowNodeInstanceDbReader(flowNodeInstanceMapper, readerConfig);
  }

  @Bean
  public GroupDbReader groupReader(
      final GroupMapper groupMapper, final RdbmsReaderConfig readerConfig) {
    return new GroupDbReader(groupMapper, readerConfig);
  }

  @Bean
  public GroupMemberDbReader groupMemberReader(
      final GroupMapper groupMapper, final RdbmsReaderConfig readerConfig) {
    return new GroupMemberDbReader(groupMapper, readerConfig);
  }

  @Bean
  public IncidentDbReader incidentReader(
      final IncidentMapper incidentMapper, final RdbmsReaderConfig readerConfig) {
    return new IncidentDbReader(incidentMapper, readerConfig);
  }

  @Bean
  public ProcessDefinitionDbReader processDefinitionReader(
      final ProcessDefinitionMapper processDefinitionMapper, final RdbmsReaderConfig readerConfig) {
    return new ProcessDefinitionDbReader(processDefinitionMapper, readerConfig);
  }

  @Bean
  public ProcessDefinitionStatisticsDbReader processDefinitionStatisticsReader(
      final ProcessDefinitionMapper processDefinitionMapper, final RdbmsReaderConfig readerConfig) {
    return new ProcessDefinitionStatisticsDbReader(processDefinitionMapper, readerConfig);
  }

  @Bean
  public ProcessInstanceDbReader processInstanceReader(
      final ProcessInstanceMapper processInstanceMapper, final RdbmsReaderConfig readerConfig) {
    return new ProcessInstanceDbReader(processInstanceMapper, readerConfig);
  }

  @Bean
  public ProcessInstanceStatisticsDbReader processInstanceStatisticsReader(
      final ProcessInstanceMapper processInstanceMapper, final RdbmsReaderConfig readerConfig) {
    return new ProcessInstanceStatisticsDbReader(processInstanceMapper, readerConfig);
  }

  @Bean
  public TenantDbReader tenantReader(
      final TenantMapper tenantMapper, final RdbmsReaderConfig readerConfig) {
    return new TenantDbReader(tenantMapper, readerConfig);
  }

  @Bean
  public TenantMemberDbReader tenantMemberReader(
      final TenantMapper tenantMapper, final RdbmsReaderConfig readerConfig) {
    return new TenantMemberDbReader(tenantMapper, readerConfig);
  }

  @Bean
  public UserDbReader userReader(
      final UserMapper userTaskMapper, final RdbmsReaderConfig readerConfig) {
    return new UserDbReader(userTaskMapper, readerConfig);
  }

  @Bean
  public RoleDbReader roleReader(
      final RoleMapper roleMapper, final RdbmsReaderConfig readerConfig) {
    return new RoleDbReader(roleMapper, readerConfig);
  }

  @Bean
  public RoleMemberDbReader roleMemberReader(
      final RoleMapper roleMapper, final RdbmsReaderConfig readerConfig) {
    return new RoleMemberDbReader(roleMapper, readerConfig);
  }

  @Bean
  public UserTaskDbReader userTaskReader(
      final UserTaskMapper userTaskMapper, final RdbmsReaderConfig readerConfig) {
    return new UserTaskDbReader(userTaskMapper, readerConfig);
  }

  @Bean
  public FormDbReader formReader(
      final FormMapper formMapper, final RdbmsReaderConfig readerConfig) {
    return new FormDbReader(formMapper, readerConfig);
  }

  @Bean
  public MappingRuleDbReader mappingReader(
      final MappingRuleMapper mappingMapper, final RdbmsReaderConfig readerConfig) {
    return new MappingRuleDbReader(mappingMapper, readerConfig);
  }

  @Bean
  public MessageSubscriptionDbReader messageSubscriptionDbReader(
      final MessageSubscriptionMapper messageSubscriptionMapper,
      final RdbmsReaderConfig readerConfig) {
    return new MessageSubscriptionDbReader(messageSubscriptionMapper, readerConfig);
  }

  @Bean
  public ProcessDefinitionMessageSubscriptionStatisticsDbReader
      processDefinitionMessageSubscriptionStatisticsDbReader(
          final MessageSubscriptionMapper messageSubscriptionMapper,
          final RdbmsReaderConfig readerConfig) {
    return new ProcessDefinitionMessageSubscriptionStatisticsDbReader(
        messageSubscriptionMapper, readerConfig);
  }

  @Bean
  public BatchOperationDbReader batchOperationReader(
      final BatchOperationMapper batchOperationMapper, final RdbmsReaderConfig readerConfig) {
    return new BatchOperationDbReader(batchOperationMapper, readerConfig);
  }

  @Bean
  public SequenceFlowDbReader sequenceFlowReader(
      final SequenceFlowMapper sequenceFlowMapper, final RdbmsReaderConfig readerConfig) {
    return new SequenceFlowDbReader(sequenceFlowMapper, readerConfig);
  }

  @Bean
  public BatchOperationItemDbReader batchOperationItemReader(
      final BatchOperationMapper batchOperationMapper, final RdbmsReaderConfig readerConfig) {
    return new BatchOperationItemDbReader(batchOperationMapper, readerConfig);
  }

  @Bean
  public JobMetricsBatchDbReader jobMetricsBatchReader(
      final JobMetricsBatchMapper jobMetricsBatchMapper) {
    return new JobMetricsBatchDbReader(jobMetricsBatchMapper);
  }

  @Bean
  public JobDbReader jobReader(final JobMapper jobMapper, final RdbmsReaderConfig readerConfig) {
    return new JobDbReader(jobMapper, readerConfig);
  }

  @Bean
  public UsageMetricsDbReader usageMetricReader(final UsageMetricMapper usageMetricMapper) {
    return new UsageMetricsDbReader(usageMetricMapper);
  }

  @Bean
  public UsageMetricTUDbReader usageMetricTUReader(final UsageMetricTUMapper usageMetricTUMapper) {
    return new UsageMetricTUDbReader(usageMetricTUMapper);
  }

  @Bean
  public ProcessDefinitionInstanceStatisticsDbReader processDefinitionInstanceStatisticsReader(
      final ProcessDefinitionMapper processDefinitionMapper, final RdbmsReaderConfig readerConfig) {
    return new ProcessDefinitionInstanceStatisticsDbReader(processDefinitionMapper, readerConfig);
  }

  @Bean
  public ProcessDefinitionMessageSubscriptionStatisticsReader
      processDefinitionMessageSubscriptionStatisticsReader(
          final MessageSubscriptionMapper messageSubscriptionMapper,
          final RdbmsReaderConfig readerConfig) {
    return new ProcessDefinitionMessageSubscriptionStatisticsDbReader(
        messageSubscriptionMapper, readerConfig);
  }

  @Bean
  public ProcessDefinitionInstanceVersionStatisticsDbReader
      processDefinitionInstanceVersionStatisticsReader(
          final ProcessDefinitionMapper processDefinitionMapper,
          final RdbmsReaderConfig readerConfig) {
    return new ProcessDefinitionInstanceVersionStatisticsDbReader(
        processDefinitionMapper, readerConfig);
  }

  @Bean
  public HistoryDeletionDbReader historyDeletionDbReader(
      final HistoryDeletionMapper historyDeletionMapper) {
    return new HistoryDeletionDbReader(historyDeletionMapper);
  }

  @Bean
  public IncidentProcessInstanceStatisticsByErrorDbReader
      incidentProcessInstanceStatisticsByErrorReader(
          final IncidentMapper incidentMapper, final RdbmsReaderConfig readerConfig) {
    return new IncidentProcessInstanceStatisticsByErrorDbReader(incidentMapper, readerConfig);
  }

  @Bean
  public RdbmsTableRowCountMetrics rdbmsTableRowCountMetrics(
      final TableMetricsMapper tableMetricsMapper, final Camunda configuration) {
    final var metricsConfig = configuration.getData().getSecondaryStorage().getRdbms().getMetrics();
    return new RdbmsTableRowCountMetrics(
        tableMetricsMapper, metricsConfig.getTableRowCountCacheDuration());
  }

  @Bean
  public CorrelatedMessageSubscriptionDbReader correlatedMessageSubscriptionReader(
      final CorrelatedMessageSubscriptionMapper correlatedMessageSubscriptionMapper,
      final RdbmsReaderConfig readerConfig) {
    return new CorrelatedMessageSubscriptionDbReader(
        correlatedMessageSubscriptionMapper, readerConfig);
  }

  @Bean
  public IncidentProcessInstanceStatisticsByDefinitionDbReader
      incidentProcessInstanceStatisticsByDefinitionReader(
          final IncidentMapper incidentMapper, final RdbmsReaderConfig readerConfig) {
    return new IncidentProcessInstanceStatisticsByDefinitionDbReader(incidentMapper, readerConfig);
  }

  @Bean
  public PersistentWebSessionDbReader persistentWebSessionReader(
      final PersistentWebSessionMapper persistentWebSessionMapper) {
    return new PersistentWebSessionDbReader(persistentWebSessionMapper);
  }

  @Bean
  public PersistentWebSessionWriter persistentWebSessionWriter(
      final PersistentWebSessionMapper persistentWebSessionMapper) {
    return new PersistentWebSessionWriter(persistentWebSessionMapper);
  }

  @Bean
  public GlobalListenerDbReader globalListenerRdbmsReader(
      final GlobalListenerMapper globalListenerMapper, final RdbmsReaderConfig readerConfig) {
    return new GlobalListenerDbReader(globalListenerMapper, readerConfig);
  }

  @Bean
  public RdbmsWriterFactory rdbmsWriterFactory(
      final SqlSessionFactory sqlSessionFactory,
      final ExporterPositionMapper exporterPositionMapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final AuditLogMapper auditLogMapper,
      final DecisionInstanceMapper decisionInstanceMapper,
      final DecisionDefinitionMapper decisionDefinitionMapper,
      final DecisionRequirementsMapper decisionRequirementsMapper,
      final FlowNodeInstanceMapper flowNodeInstanceMapper,
      final IncidentMapper incidentMapper,
      final ProcessInstanceMapper processInstanceMapper,
      final ProcessDefinitionMapper processDefinitionMapper,
      final PurgeMapper purgeMapper,
      final UserTaskMapper userTaskMapper,
      final VariableMapper variableMapper,
      final MeterRegistry meterRegistry,
      final BatchOperationDbReader batchOperationReader,
      final JobMapper jobMapper,
      final JobMetricsBatchMapper jobMetricsBatchMapper,
      final SequenceFlowMapper sequenceFlowMapper,
      final UsageMetricMapper usageMetricMapper,
      final UsageMetricTUMapper usageMetricTUMapper,
      final BatchOperationMapper batchOperationMapper,
      final MessageSubscriptionMapper messageSubscriptionMapper,
      final CorrelatedMessageSubscriptionMapper correlatedMessageSubscriptionMapper,
      final ClusterVariableMapper clusterVariableMapper,
      final HistoryDeletionMapper historyDeletionMapper) {
    return new RdbmsWriterFactory(
        sqlSessionFactory,
        exporterPositionMapper,
        vendorDatabaseProperties,
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
  }

  @Bean
  public RdbmsService rdbmsService(
      final RdbmsWriterFactory rdbmsWriterFactory,
      final VariableDbReader variableReader,
      final ClusterVariableDbReader clusterVariableDbReader,
      final AuditLogDbReader auditLogReader,
      final AuthorizationDbReader authorizationReader,
      final DecisionDefinitionDbReader decisionDefinitionReader,
      final DecisionInstanceDbReader decisionInstanceReader,
      final DecisionRequirementsDbReader decisionRequirementsReader,
      final FlowNodeInstanceDbReader flowNodeInstanceReader,
      final GroupDbReader groupReader,
      final GroupMemberDbReader groupMemberReader,
      final IncidentDbReader incidentReader,
      final ProcessDefinitionDbReader processDefinitionReader,
      final ProcessInstanceDbReader processInstanceReader,
      final RoleDbReader roleReader,
      final RoleMemberDbReader roleMemberReader,
      final TenantDbReader tenantReader,
      final TenantMemberDbReader tenantMemberReader,
      final UserDbReader userReader,
      final UserTaskDbReader userTaskReader,
      final FormDbReader formReader,
      final MappingRuleDbReader mappingRuleReader,
      final BatchOperationDbReader batchOperationReader,
      final SequenceFlowDbReader sequenceFlowReader,
      final BatchOperationItemDbReader batchOperationItemReader,
      final JobDbReader jobReader,
      final JobMetricsBatchDbReader jobMetricsBatchReader,
      final UsageMetricsDbReader usageMetricReader,
      final UsageMetricTUDbReader usageMetricTUDbReader,
      final MessageSubscriptionDbReader messageSubscriptionReader,
      final ProcessDefinitionMessageSubscriptionStatisticsDbReader
          processDefinitionMessageSubscriptionStatisticsReader,
      final CorrelatedMessageSubscriptionDbReader correlatedMessageSubscriptionReader,
      final ProcessDefinitionInstanceStatisticsDbReader processDefinitionInstanceStatisticsReader,
      final ProcessDefinitionInstanceVersionStatisticsDbReader
          processDefinitionInstanceVersionStatisticsReader,
      final HistoryDeletionDbReader historyDeletionDbReader,
      final IncidentProcessInstanceStatisticsByErrorDbReader
          incidentProcessInstanceStatisticsByErrorReader,
      final IncidentProcessInstanceStatisticsByDefinitionDbReader
          incidentProcessInstanceStatisticsByDefinitionReader,
      final GlobalListenerDbReader globalListenerDbReader) {
    return new RdbmsService(
        rdbmsWriterFactory,
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
        clusterVariableDbReader,
        roleReader,
        roleMemberReader,
        tenantReader,
        tenantMemberReader,
        userReader,
        userTaskReader,
        formReader,
        mappingRuleReader,
        batchOperationReader,
        sequenceFlowReader,
        batchOperationItemReader,
        jobReader,
        jobMetricsBatchReader,
        usageMetricReader,
        usageMetricTUDbReader,
        messageSubscriptionReader,
        processDefinitionMessageSubscriptionStatisticsReader,
        correlatedMessageSubscriptionReader,
        processDefinitionInstanceStatisticsReader,
        processDefinitionInstanceVersionStatisticsReader,
        historyDeletionDbReader,
        incidentProcessInstanceStatisticsByErrorReader,
        incidentProcessInstanceStatisticsByDefinitionReader,
        globalListenerDbReader);
  }

  @Bean
  public CommandLineRunner logJdbcDriverInfo(final DataSource dataSource) {
    return args -> {
      try (final Connection conn = dataSource.getConnection()) {
        final DatabaseMetaData meta = conn.getMetaData();
        LOG.debug("JDBC Driver: {} {}", meta.getDriverName(), meta.getDriverVersion());
        LOG.debug("JDBC Spec: {}.{}", meta.getJDBCMajorVersion(), meta.getJDBCMinorVersion());
      }
    };
  }

  @Bean
  HealthContributor rdbmsStatusHealthIndicator(final DataSource dataSource) {
    // Equivalent to what Boot would normally wire for "db"
    return new DataSourceHealthIndicator(dataSource);
  }
}
