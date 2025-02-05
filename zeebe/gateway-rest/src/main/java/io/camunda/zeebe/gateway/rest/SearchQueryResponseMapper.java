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
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationResponse;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationSearchResponse;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionItem;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceGetQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceItem;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsItem;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionInputItem;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionOutputItem;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceItem;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.FormItem;
import io.camunda.zeebe.gateway.protocol.rest.GroupItem;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.IncidentItem;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.MappingItem;
import io.camunda.zeebe.gateway.protocol.rest.MappingSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.zeebe.gateway.protocol.rest.OwnerTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionItem;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceItem;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.RoleItem;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageResponse;
import io.camunda.zeebe.gateway.protocol.rest.TenantItem;
import io.camunda.zeebe.gateway.protocol.rest.TenantSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.UsageMetricsResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskItem;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.VariableItem;
import io.camunda.zeebe.gateway.protocol.rest.VariableSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.cache.ProcessCacheItem;
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

  public static ProcessDefinitionSearchQueryResponse toProcessDefinitionSearchQueryResponse(
      final SearchQueryResult<ProcessDefinitionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new ProcessDefinitionSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toProcessDefinitions)
                .orElseGet(Collections::emptyList));
  }

  public static ProcessInstanceSearchQueryResponse toProcessInstanceSearchQueryResponse(
      final SearchQueryResult<ProcessInstanceEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new ProcessInstanceSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toProcessInstances)
                .orElseGet(Collections::emptyList));
  }

  public static RoleSearchQueryResponse toRoleSearchQueryResponse(
      final SearchQueryResult<RoleEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new RoleSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items()).map(SearchQueryResponseMapper::toRoles).orElseGet(List::of));
  }

  public static GroupSearchQueryResponse toGroupSearchQueryResponse(
      final SearchQueryResult<GroupEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new GroupSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toGroups)
                .orElseGet(List::of));
  }

  public static TenantSearchQueryResponse toTenantSearchQueryResponse(
      final SearchQueryResult<TenantEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new TenantSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toTenants)
                .orElseGet(List::of));
  }

  public static MappingSearchQueryResponse toMappingSearchQueryResponse(
      final SearchQueryResult<MappingEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new MappingSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toMappings)
                .orElseGet(List::of));
  }

  public static DecisionDefinitionSearchQueryResponse toDecisionDefinitionSearchQueryResponse(
      final SearchQueryResult<DecisionDefinitionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new DecisionDefinitionSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toDecisionDefinitions)
                .orElseGet(Collections::emptyList));
  }

  public static DecisionRequirementsSearchQueryResponse toDecisionRequirementsSearchQueryResponse(
      final SearchQueryResult<DecisionRequirementsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new DecisionRequirementsSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toDecisionRequirements)
                .orElseGet(Collections::emptyList));
  }

  public static FlowNodeInstanceSearchQueryResponse toFlowNodeInstanceSearchQueryResponse(
      final SearchQueryResult<FlowNodeInstanceEntity> result,
      final Map<Long, ProcessCacheItem> processCacheItems) {
    final var page = toSearchQueryPageResponse(result);
    return new FlowNodeInstanceSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(instances -> toFlowNodeInstance(instances, processCacheItems))
                .orElseGet(Collections::emptyList));
  }

  public static DecisionInstanceSearchQueryResponse toDecisionInstanceSearchQueryResponse(
      final SearchQueryResult<DecisionInstanceEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new DecisionInstanceSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toDecisionInstances)
                .orElseGet(Collections::emptyList));
  }

  public static UserTaskSearchQueryResponse toUserTaskSearchQueryResponse(
      final SearchQueryResult<UserTaskEntity> result,
      final Map<Long, ProcessCacheItem> processCacheItems) {
    final var page = toSearchQueryPageResponse(result);
    return new UserTaskSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(tasks -> toUserTasks(tasks, processCacheItems))
                .orElseGet(Collections::emptyList));
  }

  public static UserSearchResponse toUserSearchQueryResponse(
      final SearchQueryResult<UserEntity> result) {
    return new UserSearchResponse()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toUsers)
                .orElseGet(Collections::emptyList));
  }

  public static IncidentSearchQueryResponse toIncidentSearchQueryResponse(
      final SearchQueryResult<IncidentEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new IncidentSearchQueryResponse()
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

  private static List<ProcessDefinitionItem> toProcessDefinitions(
      final List<ProcessDefinitionEntity> processDefinitions) {
    return processDefinitions.stream().map(SearchQueryResponseMapper::toProcessDefinition).toList();
  }

  public static ProcessDefinitionItem toProcessDefinition(final ProcessDefinitionEntity entity) {
    return new ProcessDefinitionItem()
        .processDefinitionKey(entity.processDefinitionKey())
        .name(entity.name())
        .resourceName(entity.resourceName())
        .version(entity.version())
        .versionTag(entity.versionTag())
        .processDefinitionId(entity.processDefinitionId())
        .tenantId(entity.tenantId());
  }

  private static List<ProcessInstanceItem> toProcessInstances(
      final List<ProcessInstanceEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toProcessInstance).toList();
  }

  public static ProcessInstanceItem toProcessInstance(final ProcessInstanceEntity p) {
    return new ProcessInstanceItem()
        .processInstanceKey(p.processInstanceKey())
        .processDefinitionId(p.processDefinitionId())
        .processDefinitionName(p.processDefinitionName())
        .processDefinitionVersion(p.processDefinitionVersion())
        .processDefinitionVersionTag(p.processDefinitionVersionTag())
        .processDefinitionKey(p.processDefinitionKey())
        .parentProcessInstanceKey(p.parentProcessInstanceKey())
        .parentFlowNodeInstanceKey(p.parentFlowNodeInstanceKey())
        .startDate(formatDate(p.startDate()))
        .endDate(formatDate(p.endDate()))
        .state((p.state() == null) ? null : ProcessInstanceStateEnum.fromValue(p.state().name()))
        .hasIncident(p.hasIncident())
        .tenantId(p.tenantId());
  }

  private static List<RoleItem> toRoles(final List<RoleEntity> roles) {
    return roles.stream().map(SearchQueryResponseMapper::toRole).toList();
  }

  public static RoleItem toRole(final RoleEntity roleEntity) {
    return new RoleItem().key(roleEntity.roleKey()).name(roleEntity.name());
  }

  private static List<GroupItem> toGroups(final List<GroupEntity> groups) {
    return groups.stream().map(SearchQueryResponseMapper::toGroup).toList();
  }

  public static GroupItem toGroup(final GroupEntity groupEntity) {
    return new GroupItem().groupKey(groupEntity.groupKey()).name(groupEntity.name());
  }

  private static List<TenantItem> toTenants(final List<TenantEntity> tenants) {
    return tenants.stream().map(SearchQueryResponseMapper::toTenant).toList();
  }

  public static TenantItem toTenant(final TenantEntity tenantEntity) {
    return new TenantItem()
        .tenantKey(tenantEntity.key())
        .name(tenantEntity.name())
        .description(tenantEntity.description())
        .tenantId(tenantEntity.tenantId());
  }

  private static List<MappingItem> toMappings(final List<MappingEntity> mappings) {
    return mappings.stream().map(SearchQueryResponseMapper::toMapping).toList();
  }

  public static MappingItem toMapping(final MappingEntity mappingEntity) {
    return new MappingItem()
        .mappingKey(mappingEntity.mappingKey())
        .claimName(mappingEntity.claimName())
        .claimValue(mappingEntity.claimValue())
        .name(mappingEntity.name());
  }

  private static List<DecisionDefinitionItem> toDecisionDefinitions(
      final List<DecisionDefinitionEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionDefinition).toList();
  }

  private static List<DecisionRequirementsItem> toDecisionRequirements(
      final List<DecisionRequirementsEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionRequirements).toList();
  }

  private static List<FlowNodeInstanceItem> toFlowNodeInstance(
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

  public static FlowNodeInstanceItem toFlowNodeInstance(
      final FlowNodeInstanceEntity instance, final String name) {
    return new FlowNodeInstanceItem()
        .flowNodeInstanceKey(instance.flowNodeInstanceKey())
        .flowNodeId(instance.flowNodeId())
        .flowNodeName(name)
        .processDefinitionKey(instance.processDefinitionKey())
        .processDefinitionId(instance.processDefinitionId())
        .processInstanceKey(instance.processInstanceKey())
        .incidentKey(instance.incidentKey())
        .hasIncident(instance.hasIncident())
        .startDate(formatDate(instance.startDate()))
        .endDate(formatDate(instance.endDate()))
        .state(FlowNodeInstanceItem.StateEnum.fromValue(instance.state().name()))
        .type(FlowNodeInstanceItem.TypeEnum.fromValue(instance.type().name()))
        .tenantId(instance.tenantId());
  }

  public static DecisionDefinitionItem toDecisionDefinition(final DecisionDefinitionEntity d) {
    return new DecisionDefinitionItem()
        .tenantId(d.tenantId())
        .decisionDefinitionKey(d.decisionDefinitionKey())
        .name(d.name())
        .version(d.version())
        .decisionDefinitionId(d.decisionDefinitionId())
        .decisionRequirementsKey(d.decisionRequirementsKey())
        .decisionRequirementsId(d.decisionRequirementsId());
  }

  public static DecisionRequirementsItem toDecisionRequirements(
      final DecisionRequirementsEntity d) {
    return new DecisionRequirementsItem()
        .tenantId(d.tenantId())
        .decisionRequirementsKey(d.decisionRequirementsKey())
        .decisionRequirementsName(d.name())
        .version(d.version())
        .resourceName(d.resourceName())
        .decisionRequirementsId(d.decisionRequirementsId());
  }

  private static List<UserTaskItem> toUserTasks(
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

  private static List<IncidentItem> toIncidents(final List<IncidentEntity> incidents) {
    return incidents.stream().map(SearchQueryResponseMapper::toIncident).toList();
  }

  public static IncidentItem toIncident(final IncidentEntity t) {
    return new IncidentItem()
        .incidentKey(t.incidentKey())
        .processDefinitionKey(t.processDefinitionKey())
        .processDefinitionId(t.processDefinitionId())
        .processInstanceKey(t.processInstanceKey())
        .errorType(IncidentItem.ErrorTypeEnum.fromValue(t.errorType().name()))
        .errorMessage(t.errorMessage())
        .flowNodeId(t.flowNodeId())
        .flowNodeInstanceKey(t.flowNodeInstanceKey())
        .creationTime(formatDate(t.creationTime()))
        .state(IncidentItem.StateEnum.fromValue(t.state().name()))
        .jobKey(t.jobKey())
        .tenantId(t.tenantId());
  }

  public static UserTaskItem toUserTask(final UserTaskEntity t, final String name) {
    return new UserTaskItem()
        .tenantId(t.tenantId())
        .userTaskKey(t.userTaskKey())
        .name(name)
        .processInstanceKey(t.processInstanceKey())
        .processDefinitionKey(t.processDefinitionKey())
        .elementInstanceKey(t.elementInstanceKey())
        .processDefinitionId(t.processDefinitionId())
        .state(UserTaskItem.StateEnum.fromValue(t.state().name()))
        .assignee(t.assignee())
        .candidateUsers(t.candidateUsers())
        .candidateGroups(t.candidateGroups())
        .formKey(t.formKey())
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

  public static FormItem toFormItem(final FormEntity f) {
    return new FormItem()
        .formKey(f.formKey())
        .bpmnId(f.formId())
        .version(f.version())
        .schema(f.schema())
        .tenantId(f.tenantId());
  }

  public static List<UserResponse> toUsers(final List<UserEntity> users) {
    return users.stream().map(SearchQueryResponseMapper::toUser).toList();
  }

  public static UserResponse toUser(final UserEntity user) {
    return new UserResponse()
        .key(user.userKey())
        .username(user.username())
        .email(user.email())
        .name(user.name());
  }

  private static List<DecisionInstanceItem> toDecisionInstances(
      final List<DecisionInstanceEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionInstance).toList();
  }

  public static DecisionInstanceItem toDecisionInstance(final DecisionInstanceEntity entity) {
    return new DecisionInstanceItem()
        .decisionInstanceKey(entity.decisionInstanceKey())
        .decisionInstanceId(entity.decisionInstanceId())
        .state(toDecisionInstanceStateEnum(entity.state()))
        .evaluationDate(formatDate(entity.evaluationDate()))
        .evaluationFailure(entity.evaluationFailure())
        .processDefinitionKey(entity.processDefinitionKey())
        .processInstanceKey(entity.processInstanceKey())
        .decisionDefinitionKey(entity.decisionDefinitionKey())
        .decisionDefinitionId(entity.decisionDefinitionId())
        .decisionDefinitionName(entity.decisionDefinitionName())
        .decisionDefinitionVersion(entity.decisionDefinitionVersion())
        .decisionDefinitionType(toDecisionDefinitionTypeEnum(entity.decisionDefinitionType()))
        .result(entity.result());
  }

  public static DecisionInstanceGetQueryResponse toDecisionInstanceGetQueryResponse(
      final DecisionInstanceEntity entity) {
    return new DecisionInstanceGetQueryResponse()
        .decisionInstanceKey(entity.decisionInstanceKey())
        .decisionInstanceId(entity.decisionInstanceId())
        .state(toDecisionInstanceStateEnum(entity.state()))
        .evaluationDate(formatDate(entity.evaluationDate()))
        .evaluationFailure(entity.evaluationFailure())
        .processDefinitionKey(entity.processDefinitionKey())
        .processInstanceKey(entity.processInstanceKey())
        .decisionDefinitionKey(entity.decisionDefinitionKey())
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

  public static VariableSearchQueryResponse toVariableSearchQueryResponse(
      final SearchQueryResult<VariableEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new VariableSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toVariables)
                .orElseGet(Collections::emptyList));
  }

  private static List<VariableItem> toVariables(final List<VariableEntity> variableEntities) {
    return variableEntities.stream().map(SearchQueryResponseMapper::toVariable).toList();
  }

  public static VariableItem toVariable(final VariableEntity variableEntity) {
    return new VariableItem()
        .variableKey(variableEntity.variableKey())
        .name(variableEntity.name())
        .value(variableEntity.value())
        .fullValue(variableEntity.fullValue())
        .processInstanceKey(variableEntity.processInstanceKey())
        .tenantId(variableEntity.tenantId())
        .isTruncated(variableEntity.isPreview())
        .scopeKey(variableEntity.scopeKey());
  }

  public static AuthorizationSearchResponse toAuthorizationSearchQueryResponse(
      final SearchQueryResult<AuthorizationEntity> result) {
    return new AuthorizationSearchResponse()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toAuthorizations)
                .orElseGet(Collections::emptyList));
  }

  public static List<AuthorizationResponse> toAuthorizations(
      final List<AuthorizationEntity> authorizations) {
    return authorizations.stream().map(SearchQueryResponseMapper::toAuthorization).toList();
  }

  public static AuthorizationResponse toAuthorization(final AuthorizationEntity authorization) {
    return new AuthorizationResponse()
        .authorizationKey(authorization.authorizationKey())
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

  private record RuleIdentifier(String ruleId, int ruleIndex) {}
}
