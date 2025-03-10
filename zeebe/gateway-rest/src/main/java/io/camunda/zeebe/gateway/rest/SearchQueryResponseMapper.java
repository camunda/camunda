/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.gateway.rest.ResponseMapper.formatDate;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

import io.camunda.search.entities.AdHocSubprocessActivityEntity;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceInputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceOutputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.UsageMetricsCount;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivityResult;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationResult;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceGetQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionInputItem;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionOutputItem;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceResult;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.FormResult;
import io.camunda.zeebe.gateway.protocol.rest.GroupResult;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.IncidentResult;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.MappingResult;
import io.camunda.zeebe.gateway.protocol.rest.MappingSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.zeebe.gateway.protocol.rest.OwnerTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.RoleResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageResponse;
import io.camunda.zeebe.gateway.protocol.rest.TenantResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.UsageMetricsResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserResult;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskResult;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.VariableResult;
import io.camunda.zeebe.gateway.protocol.rest.VariableSearchQueryResult;
import io.camunda.zeebe.gateway.rest.cache.ProcessCacheItem;
import io.camunda.zeebe.gateway.rest.util.KeyUtil;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SearchQueryResponseMapper {

  private SearchQueryResponseMapper() {}

  public static UsageMetricsResponse toUsageMetricsResponse(
      final UsageMetricsCount usageMetricsCount) {
    return new UsageMetricsResponse()
        .assignees(usageMetricsCount.assignees())
        .processInstances(usageMetricsCount.processInstances())
        .decisionInstances(usageMetricsCount.decisionInstances());
  }

  public static ProcessDefinitionSearchQueryResult toProcessDefinitionSearchQueryResponse(
      final SearchQueryResult<ProcessDefinitionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new ProcessDefinitionSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toProcessDefinitions)
                .orElseGet(Collections::emptyList));
  }

  public static ProcessInstanceSearchQueryResult toProcessInstanceSearchQueryResponse(
      final SearchQueryResult<ProcessInstanceEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new ProcessInstanceSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toProcessInstances)
                .orElseGet(Collections::emptyList));
  }

  public static RoleSearchQueryResult toRoleSearchQueryResponse(
      final SearchQueryResult<RoleEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new RoleSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items()).map(SearchQueryResponseMapper::toRoles).orElseGet(List::of));
  }

  public static GroupSearchQueryResult toGroupSearchQueryResponse(
      final SearchQueryResult<GroupEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new GroupSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toGroups)
                .orElseGet(List::of));
  }

  public static TenantSearchQueryResult toTenantSearchQueryResponse(
      final SearchQueryResult<TenantEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new TenantSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toTenants)
                .orElseGet(List::of));
  }

  public static MappingSearchQueryResult toMappingSearchQueryResponse(
      final SearchQueryResult<MappingEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new MappingSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toMappings)
                .orElseGet(List::of));
  }

  public static DecisionDefinitionSearchQueryResult toDecisionDefinitionSearchQueryResponse(
      final SearchQueryResult<DecisionDefinitionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new DecisionDefinitionSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toDecisionDefinitions)
                .orElseGet(Collections::emptyList));
  }

  public static DecisionRequirementsSearchQueryResult toDecisionRequirementsSearchQueryResponse(
      final SearchQueryResult<DecisionRequirementsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new DecisionRequirementsSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toDecisionRequirements)
                .orElseGet(Collections::emptyList));
  }

  public static FlowNodeInstanceSearchQueryResult toFlowNodeInstanceSearchQueryResponse(
      final SearchQueryResult<FlowNodeInstanceEntity> result,
      final Map<Long, ProcessCacheItem> processCacheItems) {
    final var page = toSearchQueryPageResponse(result);
    return new FlowNodeInstanceSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(instances -> toFlowNodeInstance(instances, processCacheItems))
                .orElseGet(Collections::emptyList));
  }

  public static DecisionInstanceSearchQueryResult toDecisionInstanceSearchQueryResponse(
      final SearchQueryResult<DecisionInstanceEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new DecisionInstanceSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toDecisionInstances)
                .orElseGet(Collections::emptyList));
  }

  public static UserTaskSearchQueryResult toUserTaskSearchQueryResponse(
      final SearchQueryResult<UserTaskEntity> result,
      final Map<Long, ProcessCacheItem> processCacheItems) {
    final var page = toSearchQueryPageResponse(result);
    return new UserTaskSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(tasks -> toUserTasks(tasks, processCacheItems))
                .orElseGet(Collections::emptyList));
  }

  public static UserSearchResult toUserSearchQueryResponse(
      final SearchQueryResult<UserEntity> result) {
    return new UserSearchResult()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toUsers)
                .orElseGet(Collections::emptyList));
  }

  public static IncidentSearchQueryResult toIncidentSearchQueryResponse(
      final SearchQueryResult<IncidentEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new IncidentSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toIncidents)
                .orElseGet(Collections::emptyList));
  }

  private static SearchQueryPageResponse toSearchQueryPageResponse(
      final SearchQueryResult<?> result) {

    final List<Object> firstSortValues =
        ofNullable(result.firstSortValues()).map(Arrays::asList).orElse(emptyList());
    final List<Object> lastSortValues =
        ofNullable(result.lastSortValues()).map(Arrays::asList).orElse(emptyList());

    return new SearchQueryPageResponse()
        .totalItems(result.total())
        .firstSortValues(firstSortValues)
        .lastSortValues(lastSortValues);
  }

  private static List<ProcessDefinitionResult> toProcessDefinitions(
      final List<ProcessDefinitionEntity> processDefinitions) {
    return processDefinitions.stream().map(SearchQueryResponseMapper::toProcessDefinition).toList();
  }

  public static ProcessDefinitionResult toProcessDefinition(final ProcessDefinitionEntity entity) {
    return new ProcessDefinitionResult()
        .processDefinitionKey(KeyUtil.keyToString(entity.processDefinitionKey()))
        .name(entity.name())
        .resourceName(entity.resourceName())
        .version(entity.version())
        .versionTag(entity.versionTag())
        .processDefinitionId(entity.processDefinitionId())
        .tenantId(entity.tenantId());
  }

  private static List<ProcessInstanceResult> toProcessInstances(
      final List<ProcessInstanceEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toProcessInstance).toList();
  }

  public static ProcessInstanceResult toProcessInstance(final ProcessInstanceEntity p) {
    return new ProcessInstanceResult()
        .processInstanceKey(KeyUtil.keyToString(p.processInstanceKey()))
        .processDefinitionId(p.processDefinitionId())
        .processDefinitionName(p.processDefinitionName())
        .processDefinitionVersion(p.processDefinitionVersion())
        .processDefinitionVersionTag(p.processDefinitionVersionTag())
        .processDefinitionKey(KeyUtil.keyToString(p.processDefinitionKey()))
        .parentProcessInstanceKey(KeyUtil.keyToString(p.parentProcessInstanceKey()))
        .parentFlowNodeInstanceKey(KeyUtil.keyToString(p.parentFlowNodeInstanceKey()))
        .startDate(formatDate(p.startDate()))
        .endDate(formatDate(p.endDate()))
        .state(toProtocolState(p.state()))
        .hasIncident(p.hasIncident())
        .tenantId(p.tenantId());
  }

  private static List<RoleResult> toRoles(final List<RoleEntity> roles) {
    return roles.stream().map(SearchQueryResponseMapper::toRole).toList();
  }

  public static RoleResult toRole(final RoleEntity roleEntity) {
    return new RoleResult()
        .roleKey(KeyUtil.keyToString(roleEntity.roleKey()))
        .name(roleEntity.name());
  }

  private static List<GroupResult> toGroups(final List<GroupEntity> groups) {
    return groups.stream().map(SearchQueryResponseMapper::toGroup).toList();
  }

  public static GroupResult toGroup(final GroupEntity groupEntity) {
    return new GroupResult()
        .groupKey(KeyUtil.keyToString(groupEntity.groupKey()))
        .name(groupEntity.name());
  }

  private static List<TenantResult> toTenants(final List<TenantEntity> tenants) {
    return tenants.stream().map(SearchQueryResponseMapper::toTenant).toList();
  }

  public static TenantResult toTenant(final TenantEntity tenantEntity) {
    return new TenantResult()
        .tenantKey(KeyUtil.keyToString(tenantEntity.key()))
        .name(tenantEntity.name())
        .description(tenantEntity.description())
        .tenantId(tenantEntity.tenantId());
  }

  private static List<MappingResult> toMappings(final List<MappingEntity> mappings) {
    return mappings.stream().map(SearchQueryResponseMapper::toMapping).toList();
  }

  public static MappingResult toMapping(final MappingEntity mappingEntity) {
    return new MappingResult()
        .mappingKey(KeyUtil.keyToString(mappingEntity.mappingKey()))
        .claimName(mappingEntity.claimName())
        .claimValue(mappingEntity.claimValue())
        .name(mappingEntity.name());
  }

  private static List<DecisionDefinitionResult> toDecisionDefinitions(
      final List<DecisionDefinitionEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionDefinition).toList();
  }

  private static List<DecisionRequirementsResult> toDecisionRequirements(
      final List<DecisionRequirementsEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionRequirements).toList();
  }

  private static List<FlowNodeInstanceResult> toFlowNodeInstance(
      final List<FlowNodeInstanceEntity> instances,
      final Map<Long, ProcessCacheItem> processCacheItems) {
    return instances.stream()
        .map(
            instance -> {
              final var flowNodeName =
                  processCacheItems
                      .getOrDefault(instance.processDefinitionKey(), ProcessCacheItem.EMPTY)
                      .getFlowNodeName(instance.flowNodeId());
              return toFlowNodeInstance(instance, flowNodeName);
            })
        .toList();
  }

  public static FlowNodeInstanceResult toFlowNodeInstance(
      final FlowNodeInstanceEntity instance, final String name) {
    return new FlowNodeInstanceResult()
        .flowNodeInstanceKey(KeyUtil.keyToString(instance.flowNodeInstanceKey()))
        .flowNodeId(instance.flowNodeId())
        .flowNodeName(name)
        .processDefinitionKey(KeyUtil.keyToString(instance.processDefinitionKey()))
        .processDefinitionId(instance.processDefinitionId())
        .processInstanceKey(KeyUtil.keyToString(instance.processInstanceKey()))
        .incidentKey(KeyUtil.keyToString(instance.incidentKey()))
        .hasIncident(instance.hasIncident())
        .startDate(formatDate(instance.startDate()))
        .endDate(formatDate(instance.endDate()))
        .state(FlowNodeInstanceResult.StateEnum.fromValue(instance.state().name()))
        .type(FlowNodeInstanceResult.TypeEnum.fromValue(instance.type().name()))
        .tenantId(instance.tenantId());
  }

  public static AdHocSubprocessActivityResult toAdHocSubprocessActivity(
      final AdHocSubprocessActivityEntity entity) {
    return new AdHocSubprocessActivityResult()
        .processDefinitionKey(entity.processDefinitionKey().toString())
        .processDefinitionId(entity.processDefinitionId())
        .adHocSubprocessId(entity.adHocSubprocessId())
        .elementId(entity.elementId())
        .elementName(entity.elementName())
        .type(AdHocSubprocessActivityResult.TypeEnum.fromValue(entity.type().name()))
        .documentation(entity.documentation())
        .tenantId(entity.tenantId());
  }

  public static DecisionDefinitionResult toDecisionDefinition(final DecisionDefinitionEntity d) {
    return new DecisionDefinitionResult()
        .tenantId(d.tenantId())
        .decisionDefinitionKey(KeyUtil.keyToString(d.decisionDefinitionKey()))
        .name(d.name())
        .version(d.version())
        .decisionDefinitionId(d.decisionDefinitionId())
        .decisionRequirementsKey(KeyUtil.keyToString(d.decisionRequirementsKey()))
        .decisionRequirementsId(d.decisionRequirementsId());
  }

  public static DecisionRequirementsResult toDecisionRequirements(
      final DecisionRequirementsEntity d) {
    return new DecisionRequirementsResult()
        .tenantId(d.tenantId())
        .decisionRequirementsKey(KeyUtil.keyToString(d.decisionRequirementsKey()))
        .decisionRequirementsName(d.name())
        .version(d.version())
        .resourceName(d.resourceName())
        .decisionRequirementsId(d.decisionRequirementsId());
  }

  private static List<UserTaskResult> toUserTasks(
      final List<UserTaskEntity> tasks, final Map<Long, ProcessCacheItem> processCacheItems) {
    return tasks.stream()
        .map(
            (final UserTaskEntity t) -> {
              final var name =
                  processCacheItems
                      .getOrDefault(t.processDefinitionKey(), ProcessCacheItem.EMPTY)
                      .getFlowNodeName(t.elementId());
              return toUserTask(t, name);
            })
        .toList();
  }

  private static List<IncidentResult> toIncidents(final List<IncidentEntity> incidents) {
    return incidents.stream().map(SearchQueryResponseMapper::toIncident).toList();
  }

  public static IncidentResult toIncident(final IncidentEntity t) {
    return new IncidentResult()
        .incidentKey(KeyUtil.keyToString(t.incidentKey()))
        .processDefinitionKey(KeyUtil.keyToString(t.processDefinitionKey()))
        .processDefinitionId(t.processDefinitionId())
        .processInstanceKey(KeyUtil.keyToString(t.processInstanceKey()))
        .errorType(IncidentResult.ErrorTypeEnum.fromValue(t.errorType().name()))
        .errorMessage(t.errorMessage())
        .flowNodeId(t.flowNodeId())
        .flowNodeInstanceKey(KeyUtil.keyToString(t.flowNodeInstanceKey()))
        .creationTime(formatDate(t.creationTime()))
        .state(IncidentResult.StateEnum.fromValue(t.state().name()))
        .jobKey(KeyUtil.keyToString(t.jobKey()))
        .tenantId(t.tenantId());
  }

  public static UserTaskResult toUserTask(final UserTaskEntity t, final String name) {
    return new UserTaskResult()
        .tenantId(t.tenantId())
        .userTaskKey(KeyUtil.keyToString(t.userTaskKey()))
        .name(name)
        .processInstanceKey(KeyUtil.keyToString(t.processInstanceKey()))
        .processDefinitionKey(KeyUtil.keyToString(t.processDefinitionKey()))
        .elementInstanceKey(KeyUtil.keyToString(t.elementInstanceKey()))
        .processDefinitionId(t.processDefinitionId())
        .state(UserTaskResult.StateEnum.fromValue(t.state().name()))
        .assignee(t.assignee())
        .candidateUsers(t.candidateUsers())
        .candidateGroups(t.candidateGroups())
        .formKey(KeyUtil.keyToString(t.formKey()))
        .elementId(t.elementId())
        .creationDate(formatDate(t.creationDate()))
        .completionDate(formatDate(t.completionDate()))
        .dueDate(formatDate(t.dueDate()))
        .followUpDate(formatDate(t.followUpDate()))
        .externalFormReference(t.externalFormReference())
        .processDefinitionVersion(t.processDefinitionVersion())
        .customHeaders(t.customHeaders())
        .priority(t.priority());
  }

  public static FormResult toFormItem(final FormEntity f) {
    return new FormResult()
        .formKey(KeyUtil.keyToString(f.formKey()))
        .formId(f.formId())
        .version(f.version())
        .schema(f.schema())
        .tenantId(f.tenantId());
  }

  public static List<UserResult> toUsers(final List<UserEntity> users) {
    return users.stream().map(SearchQueryResponseMapper::toUser).toList();
  }

  public static UserResult toUser(final UserEntity user) {
    return new UserResult()
        .userKey(KeyUtil.keyToString(user.userKey()))
        .username(user.username())
        .email(user.email())
        .name(user.name());
  }

  private static List<DecisionInstanceResult> toDecisionInstances(
      final List<DecisionInstanceEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionInstance).toList();
  }

  public static DecisionInstanceResult toDecisionInstance(final DecisionInstanceEntity entity) {
    return new DecisionInstanceResult()
        .decisionInstanceKey(KeyUtil.keyToString(entity.decisionInstanceKey()))
        .decisionInstanceId(entity.decisionInstanceId())
        .state(toDecisionInstanceStateEnum(entity.state()))
        .evaluationDate(formatDate(entity.evaluationDate()))
        .evaluationFailure(entity.evaluationFailure())
        .processDefinitionKey(KeyUtil.keyToString(entity.processDefinitionKey()))
        .processInstanceKey(KeyUtil.keyToString(entity.processInstanceKey()))
        .decisionDefinitionKey(KeyUtil.keyToString(entity.decisionDefinitionKey()))
        .decisionDefinitionId(entity.decisionDefinitionId())
        .decisionDefinitionName(entity.decisionDefinitionName())
        .decisionDefinitionVersion(entity.decisionDefinitionVersion())
        .decisionDefinitionType(toDecisionDefinitionTypeEnum(entity.decisionDefinitionType()))
        .result(entity.result());
  }

  public static DecisionInstanceGetQueryResult toDecisionInstanceGetQueryResponse(
      final DecisionInstanceEntity entity) {
    return new DecisionInstanceGetQueryResult()
        .decisionInstanceKey(KeyUtil.keyToString(entity.decisionInstanceKey()))
        .decisionInstanceId(entity.decisionInstanceId())
        .state(toDecisionInstanceStateEnum(entity.state()))
        .evaluationDate(formatDate(entity.evaluationDate()))
        .evaluationFailure(entity.evaluationFailure())
        .processDefinitionKey(KeyUtil.keyToString(entity.processDefinitionKey()))
        .processInstanceKey(KeyUtil.keyToString(entity.processInstanceKey()))
        .decisionDefinitionKey(KeyUtil.keyToString(entity.decisionDefinitionKey()))
        .decisionDefinitionId(entity.decisionDefinitionId())
        .decisionDefinitionName(entity.decisionDefinitionName())
        .decisionDefinitionVersion(entity.decisionDefinitionVersion())
        .decisionDefinitionType(toDecisionDefinitionTypeEnum(entity.decisionDefinitionType()))
        .result(entity.result())
        .evaluatedInputs(toEvaluatedInputs(entity.evaluatedInputs()))
        .matchedRules(toMatchedRules(entity.evaluatedOutputs()));
  }

  private static List<EvaluatedDecisionInputItem> toEvaluatedInputs(
      final List<DecisionInstanceInputEntity> decisionInstanceInputEntities) {
    if (decisionInstanceInputEntities == null) {
      return null;
    }
    return decisionInstanceInputEntities.stream()
        .map(
            input ->
                new EvaluatedDecisionInputItem()
                    .inputId(input.inputId())
                    .inputName(input.inputName())
                    .inputValue(input.inputValue()))
        .toList();
  }

  private static List<MatchedDecisionRuleItem> toMatchedRules(
      final List<DecisionInstanceOutputEntity> decisionInstanceOutputEntities) {
    if (decisionInstanceOutputEntities == null) {
      return null;
    }
    final var outputEntitiesMappedByRule =
        decisionInstanceOutputEntities.stream()
            .collect(Collectors.groupingBy(e -> new RuleIdentifier(e.ruleId(), e.ruleIndex())));
    return outputEntitiesMappedByRule.entrySet().stream()
        .map(
            entry -> {
              final var ruleIdentifier = entry.getKey();
              final var outputs = entry.getValue();
              return new MatchedDecisionRuleItem()
                  .ruleId(ruleIdentifier.ruleId())
                  .ruleIndex(ruleIdentifier.ruleIndex())
                  .evaluatedOutputs(
                      outputs.stream()
                          .map(
                              output ->
                                  new EvaluatedDecisionOutputItem()
                                      .outputId(output.outputId())
                                      .outputName(output.outputName())
                                      .outputValue(output.outputValue()))
                          .toList());
            })
        .toList();
  }

  private static DecisionInstanceStateEnum toDecisionInstanceStateEnum(
      final DecisionInstanceState state) {
    if (state == null) {
      return null;
    }
    return switch (state) {
      case EVALUATED -> DecisionInstanceStateEnum.EVALUATED;
      case FAILED -> DecisionInstanceStateEnum.FAILED;
      case UNSPECIFIED -> DecisionInstanceStateEnum.UNSPECIFIED;
      default -> DecisionInstanceStateEnum.UNKNOWN;
    };
  }

  private static DecisionDefinitionTypeEnum toDecisionDefinitionTypeEnum(
      final DecisionDefinitionType decisionDefinitionType) {
    if (decisionDefinitionType == null) {
      return null;
    }
    return switch (decisionDefinitionType) {
      case DECISION_TABLE -> DecisionDefinitionTypeEnum.DECISION_TABLE;
      case LITERAL_EXPRESSION -> DecisionDefinitionTypeEnum.LITERAL_EXPRESSION;
      case UNSPECIFIED -> DecisionDefinitionTypeEnum.UNSPECIFIED;
      default -> DecisionDefinitionTypeEnum.UNKNOWN;
    };
  }

  public static VariableSearchQueryResult toVariableSearchQueryResponse(
      final SearchQueryResult<VariableEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new VariableSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toVariables)
                .orElseGet(Collections::emptyList));
  }

  private static List<VariableResult> toVariables(final List<VariableEntity> variableEntities) {
    return variableEntities.stream().map(SearchQueryResponseMapper::toVariable).toList();
  }

  public static VariableResult toVariable(final VariableEntity variableEntity) {
    return new VariableResult()
        .variableKey(KeyUtil.keyToString(variableEntity.variableKey()))
        .name(variableEntity.name())
        .value(variableEntity.value())
        .fullValue(variableEntity.fullValue())
        .processInstanceKey(KeyUtil.keyToString(variableEntity.processInstanceKey()))
        .tenantId(variableEntity.tenantId())
        .isTruncated(variableEntity.isPreview())
        .scopeKey(KeyUtil.keyToString(variableEntity.scopeKey()));
  }

  public static AuthorizationSearchResult toAuthorizationSearchQueryResponse(
      final SearchQueryResult<AuthorizationEntity> result) {
    return new AuthorizationSearchResult()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toAuthorizations)
                .orElseGet(Collections::emptyList));
  }

  public static List<AuthorizationResult> toAuthorizations(
      final List<AuthorizationEntity> authorizations) {
    return authorizations.stream().map(SearchQueryResponseMapper::toAuthorization).toList();
  }

  public static AuthorizationResult toAuthorization(final AuthorizationEntity authorization) {
    return new AuthorizationResult()
        .authorizationKey(KeyUtil.keyToString(authorization.authorizationKey()))
        .ownerId(authorization.ownerId())
        .ownerType(OwnerTypeEnum.fromValue(authorization.ownerType()))
        .resourceType(ResourceTypeEnum.valueOf(authorization.resourceType()))
        .resourceId(authorization.resourceId())
        .permissionTypes(
            authorization.permissionTypes().stream()
                .map(PermissionType::name)
                .map(PermissionTypeEnum::fromValue)
                .toList());
  }

  private static ProcessInstanceStateEnum toProtocolState(
      final ProcessInstanceEntity.ProcessInstanceState value) {
    if (value == null) {
      return null;
    }
    if (value == ProcessInstanceEntity.ProcessInstanceState.CANCELED) {
      return ProcessInstanceStateEnum.TERMINATED;
    }
    return ProcessInstanceStateEnum.fromValue(value.name());
  }

  private record RuleIdentifier(String ruleId, int ruleIndex) {}
}
