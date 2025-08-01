/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

public record SearchClientReaders(
    AuthorizationReader authorizationReader,
    BatchOperationReader batchOperationReader,
    BatchOperationItemReader batchOperationItemReader,
    DecisionDefinitionReader decisionDefinitionReader,
    DecisionInstanceReader decisionInstanceReader,
    DecisionRequirementsReader decisionRequirementsReader,
    FlowNodeInstanceReader flowNodeInstanceReader,
    FormReader formReader,
    GroupReader groupReader,
    GroupMemberReader groupMemberReader,
    IncidentReader incidentReader,
    JobReader jobReader,
    MappingRuleReader mappingRuleReader,
    MessageSubscriptionReader messageSubscriptionReader,
    ProcessDefinitionReader processDefinitionReader,
    ProcessDefinitionStatisticsReader processDefinitionStatisticsReader,
    ProcessInstanceReader processInstanceReader,
    ProcessInstanceStatisticsReader processInstanceStatisticsReader,
    RoleReader roleReader,
    RoleMemberReader roleMemberReader,
    SequenceFlowReader sequenceFlowReader,
    TenantReader tenantReader,
    TenantMemberReader tenantMemberReader,
    UsageMetricsReader usageMetricsReader,
    UsageMetricsTUReader usageMetricsTUReader,
    UserReader userReader,
    UserTaskReader userTaskReader,
    VariableReader variableReader) {}
