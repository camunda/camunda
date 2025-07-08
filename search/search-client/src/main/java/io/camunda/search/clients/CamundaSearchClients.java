/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static io.camunda.search.exception.ErrorMessages.ERROR_ENTITY_BY_KEY_NOT_FOUND;

import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.clients.reader.SearchEntityReader;
import io.camunda.search.clients.reader.SearchStatisticsReader;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.security.ResourceAccessController;
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
import io.camunda.search.entities.MappingEntity;
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
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.search.exception.ErrorMessages;
import io.camunda.search.exception.ResourceAccessForbiddenException;
import io.camunda.search.exception.TenantAccessForbiddenException;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceStatisticsFilter;
import io.camunda.search.page.SearchQueryPage.SearchQueryResultType;
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
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessDefinitionStatisticsQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.ProcessInstanceStatisticsQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SequenceFlowQuery;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.TypedSearchAggregationQuery;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.SecurityContext;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaSearchClients implements SearchClientsProxy {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaSearchClients.class);

  private final SearchClientReaders readers;
  private final ResourceAccessController resourceAccessController;
  private final SecurityContext securityContext;

  public CamundaSearchClients(
      final SearchClientReaders readers, final ResourceAccessController resourceAccessController) {
    this(readers, resourceAccessController, null);
  }

  public CamundaSearchClients(
      final SearchClientReaders readers,
      final ResourceAccessController resourceAccessController,
      final SecurityContext securityContext) {
    this.readers = readers;
    this.resourceAccessController = resourceAccessController;
    this.securityContext = securityContext;
  }

  @Override
  public GroupEntity getGroupById(final String groupId) {
    return doGetWithResourceAccessChecks(readers.groupReader(), groupId)
        .orElseThrow(() -> entityNotFoundException("Group", groupId));
  }

  @Override
  public SearchQueryResult<GroupEntity> searchGroups(final GroupQuery query) {
    return doSearchWithResourceAccessChecks(readers.groupReader(), query);
  }

  @Override
  public SearchQueryResult<GroupMemberEntity> searchGroupMembers(final GroupQuery query) {
    return doSearchWithResourceAccessChecks(readers.groupMemberReader(), query);
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery query) {
    return doSearchWithResourceAccessChecks(readers.authorizationReader(), query);
  }

  @Override
  public AuthorizationEntity getAuthorizationByKey(final long key) {
    return doGetWithResourceAccessChecks(readers.authorizationReader(), String.valueOf(key))
        .orElseThrow(() -> entityNotFoundException("Authorization", String.valueOf(key)));
  }

  @Override
  public SearchQueryResult<BatchOperationEntity> searchBatchOperations(
      final BatchOperationQuery query) {
    return doSearchWithResourceAccessChecks(readers.batchOperationReader(), query);
  }

  @Override
  public SearchQueryResult<BatchOperationItemEntity> searchBatchOperationItems(
      final BatchOperationItemQuery query) {
    return doSearchWithResourceAccessChecks(readers.batchOperationItemReader(), query);
  }

  @Override
  public BatchOperationEntity getBatchOperationByKey(final String key) {
    return doGetWithResourceAccessChecks(readers.batchOperationReader(), key)
        .orElseThrow(() -> entityNotFoundException("Batch Operation", key));
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery query) {
    return doSearchWithResourceAccessChecks(readers.decisionDefinitionReader(), query);
  }

  @Override
  public DecisionDefinitionEntity getDecisionDefinitionByKey(final long decisionDefinitionKey) {
    final var id = String.valueOf(decisionDefinitionKey);
    return doGetWithResourceAccessChecks(readers.decisionDefinitionReader(), id)
        .orElseThrow(() -> entityNotFoundException("Decision Definition", id));
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery query) {
    return doSearchWithResourceAccessChecks(readers.decisionInstanceReader(), query);
  }

  @Override
  public DecisionInstanceEntity getDecisionInstanceById(final String decisionInstanceId) {
    return doGetWithResourceAccessChecks(readers.decisionInstanceReader(), decisionInstanceId)
        .orElseThrow(() -> entityNotFoundException("Decision Instance", decisionInstanceId));
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery query) {
    return doSearchWithResourceAccessChecks(readers.decisionRequirementsReader(), query);
  }

  @Override
  public DecisionRequirementsEntity getDecisionRequirementsByKey(
      final long decisionRequirementsKey, final boolean includeXml) {
    final var id = String.valueOf(decisionRequirementsKey);
    return doGetWithResourceAccessChecks(
            a -> readers.decisionRequirementsReader().getByKey(id, includeXml, a))
        .orElseThrow(() -> entityNotFoundException("Decision Requirements", id));
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery query) {
    return doSearchWithResourceAccessChecks(readers.flowNodeInstanceReader(), query);
  }

  @Override
  public FlowNodeInstanceEntity getFlowNodeInstanceByKey(final Long key) {
    return doGetWithResourceAccessChecks(readers.flowNodeInstanceReader(), String.valueOf(key))
        .orElseThrow(() -> entityNotFoundException("Element Instance", String.valueOf(key)));
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(final FormQuery query) {
    return doSearchWithResourceAccessChecks(readers.formReader(), query);
  }

  @Override
  public FormEntity getFormByKey(final long key) {
    return doGetWithResourceAccessChecks(readers.formReader(), String.valueOf(key))
        .orElseThrow(() -> entityNotFoundException("Form", String.valueOf(key)));
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery query) {
    return doSearchWithResourceAccessChecks(readers.incidentReader(), query);
  }

  @Override
  public IncidentEntity getIncidentByKey(final long key) {
    return doGetWithResourceAccessChecks(readers.incidentReader(), String.valueOf(key))
        .orElseThrow(() -> entityNotFoundException("Incident", String.valueOf(key)));
  }

  @Override
  public SearchQueryResult<JobEntity> searchJobs(final JobQuery query) {
    return doSearchWithResourceAccessChecks(readers.jobReader(), query);
  }

  @Override
  public SearchQueryResult<MappingEntity> searchMappings(final MappingQuery query) {
    return doSearchWithResourceAccessChecks(readers.mappingReader(), query);
  }

  @Override
  public MappingEntity getMappingByKey(final String key) {
    return doGetWithResourceAccessChecks(readers.mappingReader(), key)
        .orElseThrow(() -> entityNotFoundException("Mapping", key));
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery query) {
    return doSearchWithResourceAccessChecks(readers.processDefinitionReader(), query);
  }

  @Override
  public ProcessDefinitionEntity getProcessDefinitionByKey(final long processDefinitionKey) {
    return doGetWithResourceAccessChecks(
            readers.processDefinitionReader(), String.valueOf(processDefinitionKey))
        .orElseThrow(
            () ->
                entityNotFoundException(
                    "Process Definition", String.valueOf(processDefinitionKey)));
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processDefinitionFlowNodeStatistics(
      final ProcessDefinitionStatisticsFilter filter) {
    return doAggregateWithResourceAccessChecks(
        readers.processDefinitionStatisticsReader(), new ProcessDefinitionStatisticsQuery(filter));
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery query) {
    return doSearchWithResourceAccessChecks(readers.processInstanceReader(), query);
  }

  @Override
  public ProcessInstanceEntity getProcessInstanceByKey(final long processInstanceKey) {
    return doGetWithResourceAccessChecks(
            readers.processInstanceReader(), String.valueOf(processInstanceKey))
        .orElseThrow(
            () -> entityNotFoundException("Process Instance", String.valueOf(processInstanceKey)));
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processInstanceFlowNodeStatistics(
      final long processInstanceKey) {
    return doAggregateWithResourceAccessChecks(
        readers.processInstanceStatisticsReader(),
        new ProcessInstanceStatisticsQuery(
            new ProcessInstanceStatisticsFilter(processInstanceKey)));
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery query) {
    return doSearchWithResourceAccessChecks(readers.roleReader(), query);
  }

  @Override
  public RoleEntity getRoleByKey(final String key) {
    return doGetWithResourceAccessChecks(readers.roleReader(), key)
        .orElseThrow(() -> entityNotFoundException("Role", key));
  }

  @Override
  public SearchQueryResult<RoleMemberEntity> searchRoleMembers(final RoleQuery query) {
    return doSearchWithResourceAccessChecks(readers.roleMemberReader(), query);
  }

  @Override
  public SearchQueryResult<SequenceFlowEntity> searchSequenceFlows(final SequenceFlowQuery query) {
    return doSearchWithResourceAccessChecks(readers.sequenceFlowReader(), query);
  }

  @Override
  public SearchQueryResult<TenantEntity> searchTenants(final TenantQuery query) {
    return doSearchWithResourceAccessChecks(readers.tenantReader(), query);
  }

  @Override
  public SearchQueryResult<TenantMemberEntity> searchTenantMembers(final TenantQuery query) {
    return doSearchWithResourceAccessChecks(readers.tenantMemberReader(), query);
  }

  @Override
  public TenantEntity getTenantByKey(final String tenantKey) {
    return doGetWithResourceAccessChecks(readers.tenantReader(), tenantKey)
        .orElseThrow(() -> entityNotFoundException("Tenant", tenantKey));
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery query) {
    return doSearchWithResourceAccessChecks(readers.userReader(), query);
  }

  @Override
  public UserEntity getUserByUsername(final String username) {
    return doGetWithResourceAccessChecks(readers.userReader(), username)
        .orElseThrow(() -> entityNotFoundException("User", username));
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery query) {
    return doSearchWithResourceAccessChecks(readers.variableReader(), query);
  }

  @Override
  public VariableEntity getVariableByKey(final long variableKey) {
    return doGetWithResourceAccessChecks(readers.variableReader(), String.valueOf(variableKey))
        .orElseThrow(() -> entityNotFoundException("Variable", String.valueOf(variableKey)));
  }

  @Override
  public SearchQueryResult<MessageSubscriptionEntity> searchMessageSubscriptions(
      final MessageSubscriptionQuery query) {
    return doSearchWithResourceAccessChecks(readers.messageSubscriptionReader(), query);
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

  private Long distinctCountUsageMetricsFor(final String event, final UsageMetricsQuery query) {
    final var userMetricsQuery =
        UsageMetricsQuery.of(
            b ->
                b.filter(
                        f ->
                            f.startTime(query.filter().startTime())
                                .endTime(query.filter().endTime())
                                .events(event))
                    .unlimited());
    return doSearchWithResourceAccessChecks(readers.usageMetricsReader(), userMetricsQuery)
        .items()
        .stream()
        .map(UsageMetricsEntity::value)
        .distinct()
        .count();
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery query) {
    return doSearchWithResourceAccessChecks(readers.userTaskReader(), query);
  }

  @Override
  public UserTaskEntity getUserTaskByKey(final long key) {
    return doGetWithResourceAccessChecks(readers.userTaskReader(), String.valueOf(key))
        .orElseThrow(() -> entityNotFoundException("User Task", String.valueOf(key)));
  }

  protected <T, Q extends TypedSearchQuery<?, ?>>
      SearchQueryResult<T> doSearchWithResourceAccessChecks(
          final SearchEntityReader<T, Q> reader, final Q query) {
    final SearchQueryResult<T> finalResult;
    SearchQueryResult<T> result;
    try {
      result = resourceAccessController.doSearch(securityContext, (a) -> reader.search(query, a));
    } catch (final ResourceAccessForbiddenException e) {
      LOG.trace(
          "Missing authorizations to execute search query, returning an empty search query result",
          e);
      result = SearchQueryResult.empty();
    } catch (final TenantAccessForbiddenException e) {
      LOG.trace("Forbidden to access tenant, returning an empty search query result", e);
      result = SearchQueryResult.empty();
    }

    finalResult = result;
    applySearchQueryResultTypeOrThrow(query, finalResult);
    return finalResult;
  }

  protected void applySearchQueryResultTypeOrThrow(
      final TypedSearchQuery<?, ?> query, final SearchQueryResult<?> result) {
    if (!SearchQueryResultType.SINGLE_RESULT.equals(query.page().resultType())) {
      return;
    }

    if (result.items().isEmpty()) {
      throw new CamundaSearchException(
          ErrorMessages.ERROR_SINGLE_RESULT_NOT_FOUND.formatted(query),
          CamundaSearchException.Reason.NOT_FOUND);
    } else if (result.items().size() > 1) {
      throw new CamundaSearchException(
          ErrorMessages.ERROR_SINGLE_RESULT_NOT_UNIQUE.formatted(query),
          CamundaSearchException.Reason.NOT_UNIQUE);
    }
  }

  protected <T, Q extends TypedSearchQuery<?, ?>> Optional<T> doGetWithResourceAccessChecks(
      final SearchEntityReader<T, Q> reader, final String key) {
    return doGetWithResourceAccessChecks(a -> reader.getByKey(key, a));
  }

  protected <T, Q extends TypedSearchQuery<?, ?>> Optional<T> doGetWithResourceAccessChecks(
      final Function<ResourceAccessChecks, T> applier) {
    try {
      final var result = resourceAccessController.doGet(securityContext, applier);
      return Optional.ofNullable(result);
    } catch (final ResourceAccessForbiddenException e) {
      LOG.trace("Forbidden to access resource, rethrowing exception", e);
      throw e;
    } catch (final TenantAccessForbiddenException e) {
      LOG.trace("Forbidden to access tenant, rethrowing exception", e);
      return Optional.empty();
    }
  }

  protected <T, A extends TypedSearchAggregationQuery<?, ?>>
      List<T> doAggregateWithResourceAccessChecks(
          final SearchStatisticsReader<T, A> reader, final A query) {
    try {
      return resourceAccessController.doAggregate(
          securityContext, (a) -> reader.aggregate(query, a));
    } catch (final ResourceAccessForbiddenException e) {
      LOG.trace(
          "Missing authorization to aggregate statistics, returning empty list of statistics", e);
      return List.of();
    } catch (final TenantAccessForbiddenException e) {
      LOG.trace("Forbidden to access tenant, returning empty list of statistics", e);
      return List.of();
    }
  }

  @Override
  public CamundaSearchClients withSecurityContext(final SecurityContext securityContext) {
    return new CamundaSearchClients(readers, resourceAccessController, securityContext);
  }

  protected CamundaSearchException entityNotFoundException(
      final String entityType, final String key) {
    throw new CamundaSearchException(
        ERROR_ENTITY_BY_KEY_NOT_FOUND.formatted(entityType, key), Reason.NOT_FOUND);
  }
}
