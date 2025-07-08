/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.read.security.DbEntityResourceAccessControl;
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
import io.camunda.db.rdbms.read.service.MappingDbReader;
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
import io.camunda.db.rdbms.sql.MappingMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.sql.RoleMapper;
import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.db.rdbms.sql.TenantMapper;
import io.camunda.db.rdbms.sql.UsageMetricMapper;
import io.camunda.db.rdbms.sql.UserMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.BatchOperationItemReader;
import io.camunda.search.clients.reader.BatchOperationReader;
import io.camunda.search.clients.reader.DecisionDefinitionReader;
import io.camunda.search.clients.reader.DecisionInstanceReader;
import io.camunda.search.clients.reader.DecisionRequirementsReader;
import io.camunda.search.clients.reader.FlowNodeInstanceReader;
import io.camunda.search.clients.reader.FormReader;
import io.camunda.search.clients.reader.GroupMemberReader;
import io.camunda.search.clients.reader.GroupReader;
import io.camunda.search.clients.reader.IncidentReader;
import io.camunda.search.clients.reader.JobReader;
import io.camunda.search.clients.reader.MappingReader;
import io.camunda.search.clients.reader.MessageSubscriptionReader;
import io.camunda.search.clients.reader.ProcessDefinitionReader;
import io.camunda.search.clients.reader.ProcessDefinitionStatisticsReader;
import io.camunda.search.clients.reader.ProcessInstanceReader;
import io.camunda.search.clients.reader.ProcessInstanceStatisticsReader;
import io.camunda.search.clients.reader.RoleMemberReader;
import io.camunda.search.clients.reader.RoleReader;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.clients.reader.SequenceFlowReader;
import io.camunda.search.clients.reader.TenantMemberReader;
import io.camunda.search.clients.reader.TenantReader;
import io.camunda.search.clients.reader.UsageMetricsReader;
import io.camunda.search.clients.reader.UserReader;
import io.camunda.search.clients.reader.UserTaskReader;
import io.camunda.search.clients.reader.VariableReader;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "camunda.database",
    name = "type",
    havingValue = DatabaseConfig.RDBMS)
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
  public ProcessDefinitionStatisticsDbReader processDefinitionStatisticsReader(
      final ProcessDefinitionMapper processDefinitionMapper) {
    return new ProcessDefinitionStatisticsDbReader(processDefinitionMapper);
  }

  @Bean
  public ProcessDefinitionDbReader processDeploymentRdbmsReader(
      final ProcessDefinitionMapper processDefinitionMapper) {
    return new ProcessDefinitionDbReader(processDefinitionMapper);
  }

  @Bean
  public MessageSubscriptionDbReader messageSubscriptionReader() {
    return new MessageSubscriptionDbReader();
  }

  @Bean
  public ProcessInstanceDbReader processRdbmsReader(
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
  public UserDbReader userRdbmsReader(final UserMapper userTaskMapper) {
    return new UserDbReader(userTaskMapper);
  }

  @Bean
  public RoleDbReader roleRdbmsReader(final RoleMapper roleMapper) {
    return new RoleDbReader(roleMapper);
  }

  @Bean
  public RoleMemberDbReader roleMemberReader() {
    return new RoleMemberDbReader();
  }

  @Bean
  public UserTaskDbReader userTaskRdbmsReader(final UserTaskMapper userTaskMapper) {
    return new UserTaskDbReader(userTaskMapper);
  }

  @Bean
  public FormDbReader formRdbmsReader(final FormMapper formMapper) {
    return new FormDbReader(formMapper);
  }

  @Bean
  public MappingDbReader mappingRdbmsReader(final MappingMapper mappingMapper) {
    return new MappingDbReader(mappingMapper);
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
  public RdbmsWriterMetrics rdbmsExporterMetrics(final MeterRegistry meterRegistry) {
    return new RdbmsWriterMetrics(meterRegistry);
  }

  @Bean
  public UsageMetricsDbReader usageMetricsReader(final UsageMetricMapper usageMetricMapper) {
    return new UsageMetricsDbReader(usageMetricMapper);
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
      final UsageMetricMapper usageMetricMapper) {
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
        usageMetricMapper);
  }

  @Bean
  public SearchClientReaders searchClientReaders(
      final AuthorizationReader authorizationReader,
      final BatchOperationReader batchOperationReader,
      final BatchOperationItemReader batchOperationItemReader,
      final DecisionDefinitionReader decisionDefinitionReader,
      final DecisionInstanceReader decisionInstanceReader,
      final DecisionRequirementsReader decisionRequirementsReader,
      final FlowNodeInstanceReader flowNodeInstanceReader,
      final FormReader formReader,
      final GroupReader groupReader,
      final GroupMemberReader groupMemberReader,
      final IncidentReader incidentReader,
      final JobReader jobReader,
      final MappingReader mappingReader,
      final MessageSubscriptionReader messageSubscriptionReader,
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessDefinitionStatisticsReader processDefinitionFlowNodeStatisticsReader,
      final ProcessInstanceReader processInstanceReader,
      final ProcessInstanceStatisticsReader processInstanceFlowNodeStatisticsReader,
      final RoleReader roleReader,
      final RoleMemberReader roleMemberReader,
      final SequenceFlowReader sequenceFlowReader,
      final TenantReader tenantReader,
      final TenantMemberReader tenantMemberReader,
      final UsageMetricsReader usageMetricsReader,
      final UserReader userReader,
      final UserTaskReader userTaskReader,
      final VariableReader variableReader) {
    return new SearchClientReaders(
        authorizationReader,
        batchOperationReader,
        batchOperationItemReader,
        decisionDefinitionReader,
        decisionInstanceReader,
        decisionRequirementsReader,
        flowNodeInstanceReader,
        formReader,
        groupReader,
        groupMemberReader,
        incidentReader,
        jobReader,
        mappingReader,
        messageSubscriptionReader,
        processDefinitionReader,
        processDefinitionFlowNodeStatisticsReader,
        processInstanceReader,
        processInstanceFlowNodeStatisticsReader,
        roleReader,
        roleMemberReader,
        sequenceFlowReader,
        tenantReader,
        tenantMemberReader,
        usageMetricsReader,
        userReader,
        userTaskReader,
        variableReader);
  }

  @Bean
  public RdbmsService rdbmsService(
      final RdbmsWriterFactory rdbmsWriterFactory, final SearchClientReaders readers) {
    return new RdbmsService(rdbmsWriterFactory, readers);
  }

  @Bean
  public DbEntityResourceAccessControl dbEntityResourceAccessControl() {
    return new DbEntityResourceAccessControl();
  }
}
