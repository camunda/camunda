/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.clients.auth.AuthorizationQueryStrategy;
import io.camunda.search.clients.auth.DocumentAuthorizationQueryStrategy;
import io.camunda.search.clients.auth.DocumentTenantQueryStrategy;
import io.camunda.search.clients.auth.TenantQueryStrategy;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.entities.UsageMetricsEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceStatisticsFilter;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.ProcessDefinitionFlowNodeStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceFlowNodeStatisticsQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SequenceFlowQuery;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import java.util.function.Function;

public class DocumentBasedSearchClients implements SearchClientsProxy {

  private final SearchClientReaders readers;
  private final AuthorizationQueryStrategy authorizationQueryStrategy;
  private final TenantQueryStrategy tenantQueryStrategy;
  private final SecurityContext securityContext;

  public DocumentBasedSearchClients(final SearchClientReaders readers) {
    this.readers = readers;
    authorizationQueryStrategy =
        new DocumentAuthorizationQueryStrategy(new AuthorizationChecker(this));
    tenantQueryStrategy = new DocumentTenantQueryStrategy();
    securityContext = null;
  }

  public DocumentBasedSearchClients(
      final SearchClientReaders readers,
      final AuthorizationQueryStrategy authorizationQueryStrategy,
      final TenantQueryStrategy tenantQueryStrategy,
      final SecurityContext securityContext) {
    this.readers = readers;
    this.authorizationQueryStrategy = authorizationQueryStrategy;
    this.tenantQueryStrategy = tenantQueryStrategy;
    this.securityContext = securityContext;
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.authorizationReader().search(query, access));
  }

  @Override
  public SearchQueryResult<SequenceFlowEntity> searchSequenceFlows(final SequenceFlowQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.sequenceFlowReader().search(query, access));
  }

  @Override
  public SearchQueryResult<MessageSubscriptionEntity> searchMessageSubscriptions(
      final MessageSubscriptionQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.messageSubscriptionReader().search(query, access));
  }

  @Override
  public DocumentBasedSearchClients withSecurityContext(final SecurityContext securityContext) {
    return new DocumentBasedSearchClients(
        readers, authorizationQueryStrategy, tenantQueryStrategy, securityContext);
  }

  @Override
  public SearchQueryResult<MappingRuleEntity> searchMappingRules(
      final MappingRuleQuery mappingQuery) {
    return executeWithResourceAccessChecks(
        access -> readers.mappingRuleReader().search(mappingQuery, access));
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.decisionDefinitionReader().search(query, access));
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.decisionInstanceReader().search(query, access));
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.decisionRequirementsReader().search(query, access));
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.flowNodeInstanceReader().search(query, access));
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(final FormQuery query) {
    return executeWithResourceAccessChecks(access -> readers.formReader().search(query, access));
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.incidentReader().search(query, access));
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery filter) {
    return executeWithResourceAccessChecks(
        access -> readers.processDefinitionReader().search(filter, access));
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processDefinitionFlowNodeStatistics(
      final ProcessDefinitionStatisticsFilter filter) {
    return executeWithResourceAccessChecks(
        access ->
            readers
                .processDefinitionStatisticsReader()
                .aggregate(new ProcessDefinitionFlowNodeStatisticsQuery(filter), access));
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery filter) {
    return executeWithResourceAccessChecks(
        access -> readers.processInstanceReader().search(filter, access));
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processInstanceFlowNodeStatistics(
      final long processInstanceKey) {
    return executeWithResourceAccessChecks(
        access ->
            readers
                .processInstanceStatisticsReader()
                .aggregate(
                    new ProcessInstanceFlowNodeStatisticsQuery(
                        new ProcessInstanceStatisticsFilter(processInstanceKey)),
                    access));
  }

  @Override
  public SearchQueryResult<JobEntity> searchJobs(final JobQuery query) {
    return executeWithResourceAccessChecks(access -> readers.jobReader().search(query, access));
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery query) {
    return executeWithResourceAccessChecks(access -> readers.roleReader().search(query, access));
  }

  @Override
  public SearchQueryResult<RoleMemberEntity> searchRoleMembers(final RoleQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.roleMemberReader().search(query, access));
  }

  @Override
  public SearchQueryResult<TenantEntity> searchTenants(final TenantQuery query) {
    return executeWithResourceAccessChecks(access -> readers.tenantReader().search(query, access));
  }

  @Override
  public SearchQueryResult<TenantMemberEntity> searchTenantMembers(final TenantQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.tenantMemberReader().search(query, access));
  }

  @Override
  public SearchQueryResult<GroupEntity> searchGroups(final GroupQuery query) {
    return executeWithResourceAccessChecks(access -> readers.groupReader().search(query, access));
  }

  @Override
  public SearchQueryResult<GroupMemberEntity> searchGroupMembers(final GroupQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.groupMemberReader().search(query, access));
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery query) {
    return executeWithResourceAccessChecks(access -> readers.userReader().search(query, access));
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.userTaskReader().search(query, access));
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.variableReader().search(query, access));
  }

  @Override
  public Long countAssignees(final UsageMetricsQuery query) {
    return distinctCountUsageMetricsFor("task_completed_by_assignee", query);
  }

  @Override
  public Long countProcessInstances(final UsageMetricsQuery query) {
    return distinctCountUsageMetricsFor("EVENT_PROCESS_INSTANCE_STARTED", query);
  }

  @Override
  public Long countDecisionInstances(final UsageMetricsQuery query) {
    return distinctCountUsageMetricsFor("EVENT_DECISION_INSTANCE_EVALUATED", query);
  }

  /*
   * The distinct count is implemented here by using Java Stream API until aggregations are in place.
   */
  private Long distinctCountUsageMetricsFor(final String event, final UsageMetricsQuery query) {
    final var finalQuery =
        UsageMetricsQuery.of(
            b ->
                b.filter(
                        f ->
                            f.startTime(query.filter().startTime())
                                .endTime(query.filter().endTime())
                                .events(event))
                    .unlimited());
    return executeWithResourceAccessChecks(
            access -> readers.usageMetricsReader().search(finalQuery, access))
        .items()
        .stream()
        .map(UsageMetricsEntity::value)
        .distinct()
        .count();
  }

  @Override
  public SearchQueryResult<BatchOperationEntity> searchBatchOperations(
      final BatchOperationQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.batchOperationReader().search(query, access));
  }

  @Override
  public SearchQueryResult<BatchOperationItemEntity> searchBatchOperationItems(
      final BatchOperationItemQuery query) {
    return executeWithResourceAccessChecks(
        access -> readers.batchOperationItemReader().search(query, access));
  }

  protected <T> T executeWithResourceAccessChecks(final Function<ResourceAccessChecks, T> applier) {
    final var authorizationCheck =
        authorizationQueryStrategy.resolveAuthorizationCheck(securityContext);
    final var tenantCheck = tenantQueryStrategy.resolveTenantCheck(securityContext);
    return applier.apply(ResourceAccessChecks.of(authorizationCheck, tenantCheck));
  }
}
