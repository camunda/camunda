/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
import org.junit.jupiter.api.Test;

class RdbmsMapperBundleTest {

  @Test
  void shouldExposeEachMapperPassedToConstructor() {
    // given
    final var auditLogMapper = mock(AuditLogMapper.class);
    final var authorizationMapper = mock(AuthorizationMapper.class);
    final var batchOperationMapper = mock(BatchOperationMapper.class);
    final var clusterVariableMapper = mock(ClusterVariableMapper.class);
    final var correlatedMessageSubscriptionMapper = mock(CorrelatedMessageSubscriptionMapper.class);
    final var decisionDefinitionMapper = mock(DecisionDefinitionMapper.class);
    final var decisionInstanceMapper = mock(DecisionInstanceMapper.class);
    final var decisionRequirementsMapper = mock(DecisionRequirementsMapper.class);
    final var deployedResourceMapper = mock(DeployedResourceMapper.class);
    final var exporterPositionMapper = mock(ExporterPositionMapper.class);
    final var flowNodeInstanceMapper = mock(FlowNodeInstanceMapper.class);
    final var formMapper = mock(FormMapper.class);
    final var globalListenerMapper = mock(GlobalListenerMapper.class);
    final var groupMapper = mock(GroupMapper.class);
    final var historyDeletionMapper = mock(HistoryDeletionMapper.class);
    final var incidentMapper = mock(IncidentMapper.class);
    final var jobMapper = mock(JobMapper.class);
    final var jobMetricsBatchMapper = mock(JobMetricsBatchMapper.class);
    final var mappingRuleMapper = mock(MappingRuleMapper.class);
    final var messageSubscriptionMapper = mock(MessageSubscriptionMapper.class);
    final var persistentWebSessionMapper = mock(PersistentWebSessionMapper.class);
    final var processDefinitionMapper = mock(ProcessDefinitionMapper.class);
    final var processInstanceMapper = mock(ProcessInstanceMapper.class);
    final var purgeMapper = mock(PurgeMapper.class);
    final var roleMapper = mock(RoleMapper.class);
    final var sequenceFlowMapper = mock(SequenceFlowMapper.class);
    final var tableMetricsMapper = mock(TableMetricsMapper.class);
    final var tenantMapper = mock(TenantMapper.class);
    final var usageMetricMapper = mock(UsageMetricMapper.class);
    final var usageMetricTUMapper = mock(UsageMetricTUMapper.class);
    final var userMapper = mock(UserMapper.class);
    final var userTaskMapper = mock(UserTaskMapper.class);
    final var variableMapper = mock(VariableMapper.class);

    // when
    final var bundle =
        new RdbmsMapperBundle(
            auditLogMapper,
            authorizationMapper,
            batchOperationMapper,
            clusterVariableMapper,
            correlatedMessageSubscriptionMapper,
            decisionDefinitionMapper,
            decisionInstanceMapper,
            decisionRequirementsMapper,
            deployedResourceMapper,
            exporterPositionMapper,
            flowNodeInstanceMapper,
            formMapper,
            globalListenerMapper,
            groupMapper,
            historyDeletionMapper,
            incidentMapper,
            jobMapper,
            jobMetricsBatchMapper,
            mappingRuleMapper,
            messageSubscriptionMapper,
            persistentWebSessionMapper,
            processDefinitionMapper,
            processInstanceMapper,
            purgeMapper,
            roleMapper,
            sequenceFlowMapper,
            tableMetricsMapper,
            tenantMapper,
            usageMetricMapper,
            usageMetricTUMapper,
            userMapper,
            userTaskMapper,
            variableMapper);

    // then
    assertThat(bundle.auditLogMapper()).isSameAs(auditLogMapper);
    assertThat(bundle.authorizationMapper()).isSameAs(authorizationMapper);
    assertThat(bundle.batchOperationMapper()).isSameAs(batchOperationMapper);
    assertThat(bundle.clusterVariableMapper()).isSameAs(clusterVariableMapper);
    assertThat(bundle.correlatedMessageSubscriptionMapper())
        .isSameAs(correlatedMessageSubscriptionMapper);
    assertThat(bundle.decisionDefinitionMapper()).isSameAs(decisionDefinitionMapper);
    assertThat(bundle.decisionInstanceMapper()).isSameAs(decisionInstanceMapper);
    assertThat(bundle.decisionRequirementsMapper()).isSameAs(decisionRequirementsMapper);
    assertThat(bundle.deployedResourceMapper()).isSameAs(deployedResourceMapper);
    assertThat(bundle.exporterPositionMapper()).isSameAs(exporterPositionMapper);
    assertThat(bundle.flowNodeInstanceMapper()).isSameAs(flowNodeInstanceMapper);
    assertThat(bundle.formMapper()).isSameAs(formMapper);
    assertThat(bundle.globalListenerMapper()).isSameAs(globalListenerMapper);
    assertThat(bundle.groupMapper()).isSameAs(groupMapper);
    assertThat(bundle.historyDeletionMapper()).isSameAs(historyDeletionMapper);
    assertThat(bundle.incidentMapper()).isSameAs(incidentMapper);
    assertThat(bundle.jobMapper()).isSameAs(jobMapper);
    assertThat(bundle.jobMetricsBatchMapper()).isSameAs(jobMetricsBatchMapper);
    assertThat(bundle.mappingRuleMapper()).isSameAs(mappingRuleMapper);
    assertThat(bundle.messageSubscriptionMapper()).isSameAs(messageSubscriptionMapper);
    assertThat(bundle.persistentWebSessionMapper()).isSameAs(persistentWebSessionMapper);
    assertThat(bundle.processDefinitionMapper()).isSameAs(processDefinitionMapper);
    assertThat(bundle.processInstanceMapper()).isSameAs(processInstanceMapper);
    assertThat(bundle.purgeMapper()).isSameAs(purgeMapper);
    assertThat(bundle.roleMapper()).isSameAs(roleMapper);
    assertThat(bundle.sequenceFlowMapper()).isSameAs(sequenceFlowMapper);
    assertThat(bundle.tableMetricsMapper()).isSameAs(tableMetricsMapper);
    assertThat(bundle.tenantMapper()).isSameAs(tenantMapper);
    assertThat(bundle.usageMetricMapper()).isSameAs(usageMetricMapper);
    assertThat(bundle.usageMetricTUMapper()).isSameAs(usageMetricTUMapper);
    assertThat(bundle.userMapper()).isSameAs(userMapper);
    assertThat(bundle.userTaskMapper()).isSameAs(userTaskMapper);
    assertThat(bundle.variableMapper()).isSameAs(variableMapper);
  }
}
