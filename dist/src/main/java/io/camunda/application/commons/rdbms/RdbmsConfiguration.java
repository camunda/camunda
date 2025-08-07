/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.application.commons.condition.ConditionalOnSecondaryStorageType;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.read.service.AuthorizationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationItemDbReader;
import io.camunda.db.rdbms.read.service.DecisionDefinitionDbReader;
import io.camunda.db.rdbms.read.service.DecisionInstanceDbReader;
import io.camunda.db.rdbms.read.service.DecisionRequirementsDbReader;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceDbReader;
import io.camunda.db.rdbms.read.service.FormDbReader;
import io.camunda.db.rdbms.read.service.GroupDbReader;
import io.camunda.db.rdbms.read.service.GroupMemberDbReader;
import io.camunda.db.rdbms.read.service.IncidentDbReader;
import io.camunda.db.rdbms.read.service.JobDbReader;
import io.camunda.db.rdbms.read.service.MappingRuleDbReader;
import io.camunda.db.rdbms.read.service.MessageSubscriptionDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceStatisticsDbReader;
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
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.sql.MappingRuleMapper;
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
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnSecondaryStorageType(DatabaseConfig.RDBMS)
@Import(MyBatisConfiguration.class)
public class RdbmsConfiguration {

  @Bean
  public VariableDbReader variableRdbmsReader(final VariableMapper variableMapper) {
    return new VariableDbReader(variableMapper);
  }

  @Bean
  public AuthorizationDbReader authorizationReader(final AuthorizationMapper authorizationMapper) {
    return new AuthorizationDbReader(authorizationMapper);
  }

  @Bean
  public DecisionDefinitionDbReader decisionDefinitionReader(
      final DecisionDefinitionMapper decisionDefinitionMapper) {
    return new DecisionDefinitionDbReader(decisionDefinitionMapper);
  }

  @Bean
  public DecisionInstanceDbReader decisionInstanceReader(
      final DecisionInstanceMapper decisionInstanceMapper) {
    return new DecisionInstanceDbReader(decisionInstanceMapper);
  }

  @Bean
  public DecisionRequirementsDbReader decisionRequirementsReader(
      final DecisionRequirementsMapper decisionRequirementsMapper) {
    return new DecisionRequirementsDbReader(decisionRequirementsMapper);
  }

  @Bean
  public FlowNodeInstanceDbReader flowNodeInstanceReader(
      final FlowNodeInstanceMapper flowNodeInstanceMapper) {
    return new FlowNodeInstanceDbReader(flowNodeInstanceMapper);
  }

  @Bean
  public GroupDbReader groupReader(final GroupMapper groupMapper) {
    return new GroupDbReader(groupMapper);
  }

  @Bean
  public GroupMemberDbReader groupMemberReader() {
    return new GroupMemberDbReader();
  }

  @Bean
  public IncidentDbReader incidentReader(final IncidentMapper incidentMapper) {
    return new IncidentDbReader(incidentMapper);
  }

  @Bean
  public ProcessDefinitionDbReader processDefinitionReader(
      final ProcessDefinitionMapper processDefinitionMapper) {
    return new ProcessDefinitionDbReader(processDefinitionMapper);
  }

  @Bean
  public ProcessDefinitionStatisticsDbReader processDefinitionStatisticsReader(
      final ProcessDefinitionMapper processDefinitionMapper) {
    return new ProcessDefinitionStatisticsDbReader(processDefinitionMapper);
  }

  @Bean
  public ProcessInstanceDbReader processInstanceReader(
      final ProcessInstanceMapper processInstanceMapper) {
    return new ProcessInstanceDbReader(processInstanceMapper);
  }

  @Bean
  public ProcessInstanceStatisticsDbReader processInstanceStatisticsReader(
      final ProcessInstanceMapper processInstanceMapper) {
    return new ProcessInstanceStatisticsDbReader(processInstanceMapper);
  }

  @Bean
  public TenantDbReader tenantReader(final TenantMapper tenantMapper) {
    return new TenantDbReader(tenantMapper);
  }

  @Bean
  public TenantMemberDbReader tenantMemberReader() {
    return new TenantMemberDbReader();
  }

  @Bean
  public UserDbReader userReader(final UserMapper userTaskMapper) {
    return new UserDbReader(userTaskMapper);
  }

  @Bean
  public RoleDbReader roleReader(final RoleMapper roleMapper) {
    return new RoleDbReader(roleMapper);
  }

  @Bean
  public RoleMemberDbReader roleMemberReader() {
    return new RoleMemberDbReader();
  }

  @Bean
  public UserTaskDbReader userTaskReader(final UserTaskMapper userTaskMapper) {
    return new UserTaskDbReader(userTaskMapper);
  }

  @Bean
  public FormDbReader formReader(final FormMapper formMapper) {
    return new FormDbReader(formMapper);
  }

  @Bean
  public MappingRuleDbReader mappingReader(final MappingRuleMapper mappingMapper) {
    return new MappingRuleDbReader(mappingMapper);
  }

  @Bean
  public MessageSubscriptionDbReader messageSubscriptionDbReader() {
    return new MessageSubscriptionDbReader();
  }

  @Bean
  public BatchOperationDbReader batchOperationReader(
      final BatchOperationMapper batchOperationMapper) {
    return new BatchOperationDbReader(batchOperationMapper);
  }

  @Bean
  public SequenceFlowDbReader sequenceFlowReader(final SequenceFlowMapper sequenceFlowMapper) {
    return new SequenceFlowDbReader(sequenceFlowMapper);
  }

  @Bean
  public BatchOperationItemDbReader batchOperationItemReader(
      final BatchOperationMapper batchOperationMapper) {
    return new BatchOperationItemDbReader(batchOperationMapper);
  }

  @Bean
  public JobDbReader jobReader(final JobMapper jobMapper) {
    return new JobDbReader(jobMapper);
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
  public RdbmsWriterMetrics rdbmsExporterMetrics(final MeterRegistry meterRegistry) {
    return new RdbmsWriterMetrics(meterRegistry);
  }

  @Bean
  public RdbmsWriterFactory rdbmsWriterFactory(
      final SqlSessionFactory sqlSessionFactory,
      final ExporterPositionMapper exporterPositionMapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final DecisionInstanceMapper decisionInstanceMapper,
      final FlowNodeInstanceMapper flowNodeInstanceMapper,
      final IncidentMapper incidentMapper,
      final ProcessInstanceMapper processInstanceMapper,
      final PurgeMapper purgeMapper,
      final UserTaskMapper userTaskMapper,
      final VariableMapper variableMapper,
      final RdbmsWriterMetrics metrics,
      final BatchOperationDbReader batchOperationReader,
      final JobMapper jobMapper,
      final SequenceFlowMapper sequenceFlowMapper,
      final UsageMetricMapper usageMetricMapper,
      final UsageMetricTUMapper usageMetricTUMapper,
      final BatchOperationMapper batchOperationMapper) {
    return new RdbmsWriterFactory(
        sqlSessionFactory,
        exporterPositionMapper,
        vendorDatabaseProperties,
        decisionInstanceMapper,
        flowNodeInstanceMapper,
        incidentMapper,
        processInstanceMapper,
        purgeMapper,
        userTaskMapper,
        variableMapper,
        metrics,
        batchOperationReader,
        jobMapper,
        sequenceFlowMapper,
        usageMetricMapper,
        usageMetricTUMapper,
        batchOperationMapper);
  }

  @Bean
  public RdbmsService rdbmsService(
      final RdbmsWriterFactory rdbmsWriterFactory,
      final VariableDbReader variableReader,
      final AuthorizationDbReader authorizationReader,
      final DecisionDefinitionDbReader decisionDefinitionReader,
      final DecisionInstanceDbReader decisionInstanceReader,
      final DecisionRequirementsDbReader decisionRequirementsReader,
      final FlowNodeInstanceDbReader flowNodeInstanceReader,
      final GroupDbReader groupReader,
      final IncidentDbReader incidentReader,
      final ProcessDefinitionDbReader processDefinitionReader,
      final ProcessInstanceDbReader processInstanceReader,
      final RoleDbReader roleReader,
      final TenantDbReader tenantReader,
      final UserDbReader userReader,
      final UserTaskDbReader userTaskReader,
      final FormDbReader formReader,
      final MappingRuleDbReader mappingRuleReader,
      final BatchOperationDbReader batchOperationReader,
      final SequenceFlowDbReader sequenceFlowReader,
      final BatchOperationItemDbReader batchOperationItemReader,
      final JobDbReader jobReader,
      final UsageMetricsDbReader usageMetricReader,
      final UsageMetricTUDbReader usageMetricTUDbReader) {
    return new RdbmsService(
        rdbmsWriterFactory,
        authorizationReader,
        decisionDefinitionReader,
        decisionInstanceReader,
        decisionRequirementsReader,
        flowNodeInstanceReader,
        groupReader,
        incidentReader,
        processDefinitionReader,
        processInstanceReader,
        variableReader,
        roleReader,
        tenantReader,
        userReader,
        userTaskReader,
        formReader,
        mappingRuleReader,
        batchOperationReader,
        sequenceFlowReader,
        batchOperationItemReader,
        jobReader,
        usageMetricReader,
        usageMetricTUDbReader);
  }
}
