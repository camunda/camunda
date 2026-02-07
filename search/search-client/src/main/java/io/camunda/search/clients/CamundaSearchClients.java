/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static io.camunda.search.exception.ErrorMessages.ERROR_ENTITY_BY_ID_NOT_FOUND;
import static io.camunda.search.exception.ErrorMessages.ERROR_ENTITY_BY_KEY_NOT_FOUND;

import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.clients.reader.SearchEntityReader;
import io.camunda.search.clients.reader.SearchQueryStatisticsReader;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.GlobalJobStatisticsEntity;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByErrorEntity;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessDefinitionInstanceStatisticsEntity;
import io.camunda.search.entities.ProcessDefinitionInstanceVersionStatisticsEntity;
import io.camunda.search.entities.ProcessDefinitionMessageSubscriptionStatisticsEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.search.exception.ErrorMessages;
import io.camunda.search.exception.TenantAccessDeniedException;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceStatisticsFilter;
import io.camunda.search.page.SearchQueryPage.SearchQueryResultType;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.query.CorrelatedMessageSubscriptionQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.GlobalJobStatisticsQuery;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.ProcessDefinitionFlowNodeStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceFlowNodeStatisticsQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.RoleMemberQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SequenceFlowQuery;
import io.camunda.search.query.TenantMemberQuery;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.TypedSearchAggregationQuery;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.search.query.UsageMetricsTUQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.ResourceAccessController;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaSearchClients implements SearchClientsProxy {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaSearchClients.class);

  private final SearchClientReaders readers;
  private final ResourceAccessController resourceAccessController;
  private final SecurityContext securityContext;

  public CamundaSearchClients(
      final SearchClientReaders readers,
      final ResourceAccessController resourceAccessController,
      final SecurityContext securityContext) {
    this.readers = readers;
    this.resourceAccessController = resourceAccessController;
    this.securityContext = securityContext;
  }

  @Override
  public AuthorizationEntity getAuthorization(final long key) {
    return doGetWithReader(readers.authorizationReader(), key)
        .orElseThrow(() -> entityByKeyNotFoundException("Authorization", key));
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery query) {
    return doSearchWithReader(readers.authorizationReader(), query, this::disableTenantCheck);
  }

  @Override
  public SearchQueryResult<SequenceFlowEntity> searchSequenceFlows(final SequenceFlowQuery query) {
    return doSearchWithReader(readers.sequenceFlowReader(), query);
  }

  @Override
  public SearchQueryResult<MessageSubscriptionEntity> searchMessageSubscriptions(
      final MessageSubscriptionQuery query) {
    return doSearchWithReader(readers.messageSubscriptionReader(), query);
  }

  @Override
  public SearchQueryResult<CorrelatedMessageSubscriptionEntity>
      searchCorrelatedMessageSubscriptions(final CorrelatedMessageSubscriptionQuery query) {
    return doSearchWithReader(readers.correlatedMessageSubscriptionReader(), query);
  }

  @Override
  public ClusterVariableEntity getClusterVariable(final String name, final String tenant) {
    return doGet(
            resourceAccessChecks ->
                readers
                    .clusterVariableReader()
                    .getTenantScopedClusterVariable(name, tenant, resourceAccessChecks))
        .orElseThrow(
            () ->
                entityByNameNotFoundException(
                    "Tenant-scoped Cluster Variable", "%s".formatted(name)));
  }

  @Override
  public ClusterVariableEntity getClusterVariable(final String name) {
    return doGet(
            resourceAccessChecks ->
                readers
                    .clusterVariableReader()
                    .getGloballyScopedClusterVariable(name, resourceAccessChecks))
        .orElseThrow(
            () ->
                entityByNameNotFoundException(
                    "Global-scoped Cluster Variable", "%s".formatted(name)));
  }

  @Override
  public SearchQueryResult<ClusterVariableEntity> searchClusterVariables(
      final ClusterVariableQuery query) {
    return doSearchWithReader(readers.clusterVariableReader(), query);
  }

  @Override
  public AuditLogEntity getAuditLog(final String id) {
    return doGetWithReader(readers.auditLogReader(), id)
        .orElseThrow(() -> entityByIdNotFoundException("Audit log", id));
  }

  @Override
  public SearchQueryResult<AuditLogEntity> searchAuditLogs(final AuditLogQuery query) {
    return doSearchWithReader(readers.auditLogReader(), query);
  }

  @Override
  public CamundaSearchClients withSecurityContext(final SecurityContext securityContext) {
    return new CamundaSearchClients(readers, resourceAccessController, securityContext);
  }

  @Override
  public MappingRuleEntity getMappingRule(final String id) {
    return doGetWithReader(readers.mappingRuleReader(), id)
        .orElseThrow(() -> entityByIdNotFoundException("Mapping Rule", id));
  }

  @Override
  public SearchQueryResult<MappingRuleEntity> searchMappingRules(
      final MappingRuleQuery mappingRuleQuery) {
    return doSearchWithReader(
        readers.mappingRuleReader(), mappingRuleQuery, this::disableTenantCheck);
  }

  @Override
  public DecisionDefinitionEntity getDecisionDefinition(final long key) {
    return doGetWithReader(readers.decisionDefinitionReader(), key)
        .orElseThrow(() -> entityByKeyNotFoundException("Decision Definition", key));
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery query) {
    return doSearchWithReader(readers.decisionDefinitionReader(), query);
  }

  @Override
  public DecisionInstanceEntity getDecisionInstance(final String id) {
    return doGetWithReader(readers.decisionInstanceReader(), id)
        .orElseThrow(() -> entityByIdNotFoundException("Decision Instance", id));
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery query) {
    return doSearchWithReader(readers.decisionInstanceReader(), query);
  }

  @Override
  public DecisionRequirementsEntity getDecisionRequirements(
      final long key, final boolean includeXml) {
    return doGet(a -> readers.decisionRequirementsReader().getByKey(key, a, includeXml))
        .orElseThrow(() -> entityByKeyNotFoundException("Decision Requirements", key));
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery query) {
    return doSearchWithReader(readers.decisionRequirementsReader(), query);
  }

  @Override
  public FlowNodeInstanceEntity getFlowNodeInstance(final long key) {
    return doGetWithReader(readers.flowNodeInstanceReader(), key)
        .orElseThrow(() -> entityByKeyNotFoundException("Element Instance", key));
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery query) {
    return doSearchWithReader(readers.flowNodeInstanceReader(), query);
  }

  @Override
  public FormEntity getForm(final long key) {
    return doGetWithReader(readers.formReader(), key)
        .orElseThrow(() -> entityByKeyNotFoundException("Form", key));
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(final FormQuery query) {
    return doSearchWithReader(readers.formReader(), query);
  }

  @Override
  public IncidentEntity getIncident(final long key) {
    return doGetWithReader(readers.incidentReader(), key)
        .orElseThrow(() -> entityByKeyNotFoundException("Incident", key));
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery query) {
    return doSearchWithReader(readers.incidentReader(), query);
  }

  @Override
  public SearchQueryResult<IncidentProcessInstanceStatisticsByErrorEntity>
      incidentProcessInstanceStatisticsByError(
          final IncidentProcessInstanceStatisticsByErrorQuery query) {

    return doSearchWithReader(readers.incidentProcessInstanceStatisticsByErrorReader(), query);
  }

  @Override
  public SearchQueryResult<IncidentProcessInstanceStatisticsByDefinitionEntity>
      searchIncidentProcessInstanceStatisticsByDefinition(
          final IncidentProcessInstanceStatisticsByDefinitionQuery query) {

    return doSearchWithReader(readers.incidentProcessInstanceStatisticsByDefinitionReader(), query);
  }

  @Override
  public ProcessDefinitionEntity getProcessDefinition(final long key) {
    return doGetWithReader(readers.processDefinitionReader(), key)
        .orElseThrow(() -> entityByKeyNotFoundException("Process Definition", key));
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery query) {
    return doSearchWithReader(readers.processDefinitionReader(), query);
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processDefinitionFlowNodeStatistics(
      final ProcessDefinitionStatisticsFilter filter) {
    return doReadWithResourceAccessController(
        access ->
            readers
                .processDefinitionStatisticsReader()
                .aggregate(new ProcessDefinitionFlowNodeStatisticsQuery(filter), access));
  }

  @Override
  public SearchQueryResult<ProcessDefinitionInstanceStatisticsEntity>
      processDefinitionInstanceStatistics(final ProcessDefinitionInstanceStatisticsQuery query) {
    return doSearchWithReader(readers.processDefinitionInstanceStatisticsReader(), query);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionMessageSubscriptionStatisticsEntity>
      getProcessDefinitionMessageSubscriptionStatistics(
          final ProcessDefinitionMessageSubscriptionStatisticsQuery query) {
    return doSearchWithReader(
        readers.processDefinitionMessageSubscriptionStatisticsReader(), query);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionInstanceVersionStatisticsEntity>
      processDefinitionInstanceVersionStatistics(
          final ProcessDefinitionInstanceVersionStatisticsQuery query) {
    return doSearchWithReader(readers.processDefinitionInstanceVersionStatisticsReader(), query);
  }

  @Override
  public ProcessInstanceEntity getProcessInstance(final long processInstanceKey) {
    return doGetWithReader(readers.processInstanceReader(), processInstanceKey)
        .orElseThrow(() -> entityByKeyNotFoundException("Process Instance", processInstanceKey));
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery query) {
    return doSearchWithReader(readers.processInstanceReader(), query);
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processInstanceFlowNodeStatistics(
      final long processInstanceKey) {
    return doReadWithResourceAccessController(
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
    return doSearchWithReader(readers.jobReader(), query);
  }

  @Override
  public GlobalJobStatisticsEntity getGlobalJobStatistics(final GlobalJobStatisticsQuery query) {
    return doReadWithResourceAccessController(
        access -> readers.jobMetricsBatchReader().getGlobalJobStatistics(query, access));
  }

  @Override
  public RoleEntity getRole(final String id) {
    return doGetWithReader(readers.roleReader(), id)
        .orElseThrow(() -> entityByIdNotFoundException("Role", id));
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery query) {
    return doSearchWithReader(readers.roleReader(), query, this::disableTenantCheck);
  }

  @Override
  public SearchQueryResult<RoleMemberEntity> searchRoleMembers(final RoleMemberQuery query) {
    return doSearchWithReader(readers.roleMemberReader(), query, this::disableTenantCheck);
  }

  @Override
  public TenantEntity getTenant(final String id) {
    return doGetWithReader(readers.tenantReader(), id)
        .orElseThrow(() -> entityByIdNotFoundException("Tenant", id));
  }

  @Override
  public SearchQueryResult<TenantEntity> searchTenants(final TenantQuery query) {
    return doSearchWithReader(readers.tenantReader(), query, this::disableTenantCheck);
  }

  @Override
  public SearchQueryResult<TenantMemberEntity> searchTenantMembers(final TenantMemberQuery query) {
    return doSearchWithReader(readers.tenantMemberReader(), query, this::disableTenantCheck);
  }

  @Override
  public GroupEntity getGroup(final String id) {
    return doGetWithReader(readers.groupReader(), id)
        .orElseThrow(() -> entityByIdNotFoundException("Group", id));
  }

  @Override
  public SearchQueryResult<GroupEntity> searchGroups(final GroupQuery query) {
    return doSearchWithReader(readers.groupReader(), query, this::disableTenantCheck);
  }

  @Override
  public SearchQueryResult<GroupMemberEntity> searchGroupMembers(final GroupMemberQuery query) {
    return doSearchWithReader(readers.groupMemberReader(), query, this::disableTenantCheck);
  }

  @Override
  public UserEntity getUser(final String username) {
    return doGetWithReader(readers.userReader(), username)
        .orElseThrow(() -> entityByUsernameNotFoundException(username));
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery query) {
    return doSearchWithReader(readers.userReader(), query, this::disableTenantCheck);
  }

  @Override
  public UserTaskEntity getUserTask(final long key) {
    return doGetWithReader(readers.userTaskReader(), key)
        .orElseThrow(() -> entityByKeyNotFoundException("User Task", key));
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery query) {
    return doSearchWithReader(readers.userTaskReader(), query);
  }

  @Override
  public VariableEntity getVariable(final long key) {
    return doGetWithReader(readers.variableReader(), key)
        .orElseThrow(() -> entityByKeyNotFoundException("Variable", key));
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery query) {
    return doSearchWithReader(readers.variableReader(), query);
  }

  @Override
  public UsageMetricStatisticsEntity usageMetricStatistics(final UsageMetricsQuery query) {
    return doReadWithResourceAccessController(
        access -> readers.usageMetricsReader().usageMetricStatistics(query, access));
  }

  @Override
  public UsageMetricTUStatisticsEntity usageMetricTUStatistics(final UsageMetricsTUQuery query) {
    return doReadWithResourceAccessController(
        access -> readers.usageMetricsTUReader().usageMetricTUStatistics(query, access));
  }

  @Override
  public BatchOperationEntity getBatchOperation(final String id) {
    return doGetWithReader(readers.batchOperationReader(), id)
        .orElseThrow(() -> entityByIdNotFoundException("Batch Operation", id));
  }

  @Override
  public SearchQueryResult<BatchOperationEntity> searchBatchOperations(
      final BatchOperationQuery query) {
    return doSearchWithReader(readers.batchOperationReader(), query);
  }

  @Override
  public SearchQueryResult<BatchOperationItemEntity> searchBatchOperationItems(
      final BatchOperationItemQuery query) {
    return doSearchWithReader(readers.batchOperationItemReader(), query);
  }

  protected <T, Q extends TypedSearchQuery<?, ?>> Optional<T> doGetWithReader(
      final SearchEntityReader<T, Q> reader, final long key) {
    return doGet(a -> reader.getByKey(key, a));
  }

  protected <T, Q extends TypedSearchQuery<?, ?>> Optional<T> doGetWithReader(
      final SearchEntityReader<T, Q> reader, final String id) {
    return doGet(a -> reader.getById(id, a));
  }

  protected <T, Q extends TypedSearchQuery<?, ?>> SearchQueryResult<T> doSearchWithReader(
      final SearchEntityReader<T, Q> reader, final Q query) {
    return doSearchWithReader(reader, query, Function.identity());
  }

  protected <T, Q extends TypedSearchQuery<?, ?>> SearchQueryResult<T> doSearchWithReader(
      final SearchEntityReader<T, Q> reader,
      final Q query,
      final Function<ResourceAccessChecks, ResourceAccessChecks> overwriteResourceAccessChecks) {
    return withResultTypeCheck(reader, query, overwriteResourceAccessChecks);
  }

  protected <T, A extends TypedSearchAggregationQuery<?, ?, ?>>
      SearchQueryResult<T> doSearchWithReader(
          final SearchQueryStatisticsReader<T, A> reader, final A query) {
    return withResultTypeCheck(reader, query);
  }

  protected <T, A extends TypedSearchAggregationQuery<?, ?, ?>>
      SearchQueryResult<T> withResultTypeCheck(
          final SearchQueryStatisticsReader<T, A> reader, final A query) {
    return ensureSingeResultIfNecessary(
        () -> doReadWithResourceAccessController(a -> reader.aggregate(query, a)), query);
  }

  protected <T, Q extends TypedSearchQuery<?, ?>> SearchQueryResult<T> withResultTypeCheck(
      final SearchEntityReader<T, Q> reader,
      final Q query,
      final Function<ResourceAccessChecks, ResourceAccessChecks> overwriteAccessChecks) {
    return ensureSingeResultIfNecessary(
        () ->
            doReadWithResourceAccessController(
                a -> {
                  final var finalResourceAccessChecks = overwriteAccessChecks.apply(a);
                  return reader.search(query, finalResourceAccessChecks);
                }),
        query);
  }

  protected ResourceAccessChecks disableTenantCheck(
      final ResourceAccessChecks resourceAccessChecks) {
    return ResourceAccessChecks.of(
        resourceAccessChecks.authorizationCheck(),
        TenantCheck.disabled(),
        resourceAccessChecks.authentication());
  }

  protected <T> SearchQueryResult<T> ensureSingeResultIfNecessary(
      final Supplier<SearchQueryResult<T>> resultSupplier, final TypedSearchQuery<?, ?> query) {
    final var result = resultSupplier.get();
    if (SearchQueryResultType.SINGLE_RESULT.equals(query.page().resultType())) {
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
    return result;
  }

  protected <T> Optional<T> doGet(final Function<ResourceAccessChecks, T> applier) {
    try {
      return Optional.ofNullable(doGetWithResourceAccessController(applier));
    } catch (final TenantAccessDeniedException e) {
      LOG.trace("Forbidden to access tenant, returning null", e);
      return Optional.empty();
    }
  }

  protected <T> T doReadWithResourceAccessController(
      final Function<ResourceAccessChecks, T> applier) {
    return resourceAccessController.doSearch(securityContext, applier);
  }

  protected <T> T doGetWithResourceAccessController(
      final Function<ResourceAccessChecks, T> applier) {
    return resourceAccessController.doGet(securityContext, applier);
  }

  protected CamundaSearchException entityByKeyNotFoundException(
      final String entityType, final long key) {
    return new CamundaSearchException(
        ERROR_ENTITY_BY_KEY_NOT_FOUND.formatted(entityType, key), Reason.NOT_FOUND);
  }

  protected CamundaSearchException entityByIdNotFoundException(
      final String entityType, final String id) {
    return entityByIdNotFoundException(entityType, "id", id);
  }

  protected CamundaSearchException entityByNameNotFoundException(
      final String entityType, final String id) {
    return entityByIdNotFoundException(entityType, "name", id);
  }

  protected CamundaSearchException entityByUsernameNotFoundException(final String id) {
    return entityByIdNotFoundException("User", "username", id);
  }

  private CamundaSearchException entityByIdNotFoundException(
      final String entityType, final String idType, final String id) {
    return new CamundaSearchException(
        ERROR_ENTITY_BY_ID_NOT_FOUND.formatted(entityType, idType, id), Reason.NOT_FOUND);
  }
}
