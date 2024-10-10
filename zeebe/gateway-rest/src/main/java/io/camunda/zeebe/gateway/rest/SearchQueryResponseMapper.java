/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static java.util.Optional.ofNullable;

import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceInputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceOutputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.OperationEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceReference;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.SearchQueryResult;
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
import io.camunda.zeebe.gateway.protocol.rest.IncidentItem;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.zeebe.gateway.protocol.rest.OperationItem;
import io.camunda.zeebe.gateway.protocol.rest.ProblemDetail;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionItem;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceItem;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceReferenceItem;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskItem;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.VariableItem;
import io.camunda.zeebe.gateway.protocol.rest.VariableSearchQueryResponse;
import io.camunda.zeebe.util.Either;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class SearchQueryResponseMapper {

  private SearchQueryResponseMapper() {}

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

  public static FlowNodeInstanceSearchQueryResponse toFlownodeInstanceSearchQueryResponse(
      final SearchQueryResult<FlowNodeInstanceEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new FlowNodeInstanceSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toFlownodeInstance)
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
      final SearchQueryResult<UserTaskEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new UserTaskSearchQueryResponse()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toUserTasks)
                .orElseGet(Collections::emptyList));
  }

  public static UserSearchResponse toUserSearchQueryResponse(
      final SearchQueryResult<UserEntity> result) {
    final var response = new UserSearchResponse();
    final var total = result.total();
    final var sortValues = result.sortValues();
    final var items = result.items();

    final var page = new SearchQueryPageResponse();
    page.setTotalItems(total);
    response.setPage(page);

    if (sortValues != null) {
      page.setLastSortValues(Arrays.asList(sortValues));
    }

    if (items != null) {
      response.setItems(toUsers(items).get());
    }

    return response;
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
    return new SearchQueryPageResponse()
        .totalItems(result.total())
        .lastSortValues(
            ofNullable(result.sortValues()).map(Arrays::asList).orElseGet(Collections::emptyList));
  }

  private static List<ProcessDefinitionItem> toProcessDefinitions(
      final List<ProcessDefinitionEntity> processDefinitions) {
    return processDefinitions.stream().map(SearchQueryResponseMapper::toProcessDefinition).toList();
  }

  public static ProcessDefinitionItem toProcessDefinition(final ProcessDefinitionEntity entity) {
    return new ProcessDefinitionItem()
        .processDefinitionKey(entity.key())
        .name(entity.name())
        .resourceName(entity.resourceName())
        .version(entity.version())
        .versionTag(entity.versionTag())
        .processDefinitionId(entity.bpmnProcessId())
        .tenantId(entity.tenantId());
  }

  private static List<ProcessInstanceItem> toProcessInstances(
      final List<ProcessInstanceEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toProcessInstance).toList();
  }

  public static ProcessInstanceItem toProcessInstance(final ProcessInstanceEntity p) {
    return new ProcessInstanceItem()
        .processInstanceKey(p.key())
        .processDefinitionId(p.bpmnProcessId())
        .processDefinitionName(p.processName())
        .processDefinitionVersion(p.processVersion())
        .processDefinitionVersionTag(p.processVersionTag())
        .processDefinitionKey(p.processDefinitionKey())
        .rootProcessInstanceKey(p.rootProcessInstanceKey())
        .parentProcessInstanceKey(p.parentProcessInstanceKey())
        .parentFlowNodeInstanceKey(p.parentFlowNodeInstanceKey())
        .treePath(p.treePath())
        .startDate(p.startDate())
        .endDate(p.endDate())
        .state((p.state() == null) ? null : ProcessInstanceStateEnum.fromValue(p.state().name()))
        .hasIncident(p.incident())
        .tenantId(p.tenantId());
  }

  private static List<OperationItem> toOperations(final List<OperationEntity> instances) {
    if (instances == null) {
      return null;
    }
    return instances.stream().map(SearchQueryResponseMapper::toOperation).toList();
  }

  private static OperationItem toOperation(final OperationEntity o) {
    return new OperationItem()
        .id(o.id())
        .batchOperationId(o.batchOperationId())
        .type((o.type() == null) ? null : (OperationItem.TypeEnum.fromValue(o.type())))
        .state((o.state() == null) ? null : (OperationItem.StateEnum.fromValue(o.state())))
        .errorMessage(o.errorMessage())
        .completedDate(o.completedDate());
  }

  private static List<ProcessInstanceReferenceItem> toCallHierarchy(
      final List<ProcessInstanceReference> instances) {
    if (instances == null) {
      return null;
    }
    return instances.stream().map(SearchQueryResponseMapper::toCallHierarchy).toList();
  }

  private static ProcessInstanceReferenceItem toCallHierarchy(final ProcessInstanceReference p) {
    return new ProcessInstanceReferenceItem()
        .instanceId(p.instanceId())
        .processDefinitionId(p.processDefinitionId())
        .processDefinitionName(p.processDefinitionName());
  }

  private static List<DecisionDefinitionItem> toDecisionDefinitions(
      final List<DecisionDefinitionEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionDefinition).toList();
  }

  private static List<DecisionRequirementsItem> toDecisionRequirements(
      final List<DecisionRequirementsEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionRequirements).toList();
  }

  private static List<FlowNodeInstanceItem> toFlownodeInstance(
      final List<FlowNodeInstanceEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toFlowNodeInstance).toList();
  }

  public static FlowNodeInstanceItem toFlowNodeInstance(final FlowNodeInstanceEntity instance) {
    return new FlowNodeInstanceItem()
        .flowNodeInstanceKey(instance.key())
        .flowNodeId(instance.flowNodeId())
        .processDefinitionKey(instance.processDefinitionKey())
        .processDefinitionId(instance.bpmnProcessId())
        .processInstanceKey(instance.processInstanceKey())
        .incidentKey(instance.incidentKey())
        .hasIncident(instance.incident())
        .startDate(instance.startDate())
        .endDate(instance.endDate())
        .state(FlowNodeInstanceItem.StateEnum.fromValue(instance.state().name()))
        .treePath(instance.treePath())
        .type(FlowNodeInstanceItem.TypeEnum.fromValue(instance.type().name()))
        .tenantId(instance.tenantId());
  }

  public static DecisionDefinitionItem toDecisionDefinition(final DecisionDefinitionEntity d) {
    return new DecisionDefinitionItem()
        .tenantId(d.tenantId())
        .decisionDefinitionKey(d.key())
        .name(d.name())
        .version(d.version())
        .decisionDefinitionId(d.decisionId())
        .decisionRequirementsKey(d.decisionRequirementsKey())
        .decisionRequirementsId(d.decisionRequirementsId());
  }

  public static DecisionRequirementsItem toDecisionRequirements(
      final DecisionRequirementsEntity d) {
    return new DecisionRequirementsItem()
        .tenantId(d.tenantId())
        .decisionRequirementsKey(d.key())
        .name(d.name())
        .version(d.version())
        .resourceName(d.resourceName())
        .decisionRequirementsId(d.decisionRequirementsId());
  }

  private static List<UserTaskItem> toUserTasks(final List<UserTaskEntity> tasks) {
    return tasks.stream().map(SearchQueryResponseMapper::toUserTask).toList();
  }

  private static List<IncidentItem> toIncidents(final List<IncidentEntity> incidents) {
    return incidents.stream().map(SearchQueryResponseMapper::toIncident).toList();
  }

  public static IncidentItem toIncident(final IncidentEntity t) {
    return new IncidentItem()
        .incidentKey(t.key())
        .processDefinitionKey(t.processDefinitionKey())
        .processDefinitionId(t.bpmnProcessId())
        .processInstanceKey(t.processInstanceKey())
        .errorType(IncidentItem.ErrorTypeEnum.fromValue(t.errorType().name()))
        .errorMessage(t.errorMessage())
        .flowNodeId(t.flowNodeId())
        .flowNodeInstanceKey(t.flowNodeInstanceKey())
        .creationTime(t.creationTime())
        .state(IncidentItem.StateEnum.fromValue(t.state().name()))
        .jobKey(t.jobKey())
        .treePath(t.treePath())
        .tenantId(t.tenantId());
  }

  public static UserTaskItem toUserTask(final UserTaskEntity t) {
    return new UserTaskItem()
        .tenantIds(t.tenantId())
        .userTaskKey(t.key())
        .processInstanceKey(t.processInstanceId())
        .processDefinitionKey(t.processDefinitionId())
        .elementInstanceKey(t.flowNodeInstanceId())
        .processDefinitionId(t.bpmnProcessId())
        .state(t.state())
        .assignee(t.assignee())
        .candidateUser(t.candidateUsers())
        .candidateGroup(t.candidateGroups())
        .formKey(t.formKey())
        .elementId(t.flowNodeBpmnId())
        .creationDate(t.creationTime())
        .completionDate(t.completionTime())
        .dueDate(t.dueDate())
        .followUpDate(t.followUpDate())
        .externalFormReference(t.externalFormReference())
        .processDefinitionVersion(t.processDefinitionVersion())
        .customHeaders(t.customHeaders())
        .priority(t.priority());
  }

  public static FormItem toFormItem(final FormEntity f) {
    return new FormItem()
        .formKey(Long.valueOf(f.id()))
        .bpmnId(f.bpmnId())
        .version(f.version())
        .schema(f.schema())
        .tenantId(f.tenantId());
  }

  public static Either<ProblemDetail, List<UserResponse>> toUsers(final List<UserEntity> users) {
    return Either.right(
        users.stream().map(SearchQueryResponseMapper::toUser).map(Either::get).toList());
  }

  public static Either<ProblemDetail, UserResponse> toUser(final UserEntity user) {
    return Either.right(
        new UserResponse()
            .key(user.key())
            .username(user.username())
            .email(user.email())
            .name(user.name()));
  }

  private static List<DecisionInstanceItem> toDecisionInstances(
      final List<DecisionInstanceEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionInstance).toList();
  }

  public static DecisionInstanceItem toDecisionInstance(final DecisionInstanceEntity entity) {
    return new DecisionInstanceItem()
        .decisionInstanceKey(entity.key())
        .state(toDecisionInstanceStateEnum(entity.state()))
        .evaluationDate(entity.evaluationDate())
        .evaluationFailure(entity.evaluationFailure())
        .processDefinitionKey(entity.processDefinitionKey())
        .processInstanceKey(entity.processInstanceKey())
        .decisionDefinitionKey(Long.valueOf(entity.decisionDefinitionId()))
        .decisionDefinitionId(entity.decisionId())
        .decisionDefinitionName(entity.decisionName())
        .decisionDefinitionVersion(entity.decisionVersion())
        .decisionDefinitionType(toDecisionDefinitionTypeEnum(entity.decisionType()))
        .result(entity.result());
  }

  public static DecisionInstanceGetQueryResponse toDecisionInstanceGetQueryResponse(
      final DecisionInstanceEntity entity) {
    return new DecisionInstanceGetQueryResponse()
        .decisionInstanceKey(entity.key())
        .state(toDecisionInstanceStateEnum(entity.state()))
        .evaluationDate(entity.evaluationDate())
        .evaluationFailure(entity.evaluationFailure())
        .processDefinitionKey(entity.processDefinitionKey())
        .processInstanceKey(entity.processInstanceKey())
        .decisionDefinitionKey(Long.valueOf(entity.decisionDefinitionId()))
        .decisionDefinitionId(entity.decisionId())
        .decisionDefinitionName(entity.decisionName())
        .decisionDefinitionVersion(entity.decisionVersion())
        .decisionDefinitionType(toDecisionDefinitionTypeEnum(entity.decisionType()))
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
                    .inputId(input.id())
                    .inputName(input.name())
                    .inputValue(input.value()))
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
                                      .outputId(output.id())
                                      .outputName(output.name())
                                      .outputValue(output.value()))
                          .toList());
            })
        .toList();
  }

  private static DecisionInstanceStateEnum toDecisionInstanceStateEnum(
      final DecisionInstanceState state) {
    if (state == null) {
      return null;
    }
    switch (state) {
      case EVALUATED:
        return DecisionInstanceStateEnum.EVALUATED;
      case FAILED:
        return DecisionInstanceStateEnum.FAILED;
      case UNSPECIFIED:
        return DecisionInstanceStateEnum.UNSPECIFIED;
      case UNKNOWN:
      default:
        return DecisionInstanceStateEnum.UNKNOWN;
    }
  }

  private static DecisionDefinitionTypeEnum toDecisionDefinitionTypeEnum(
      final DecisionDefinitionType decisionDefinitionType) {
    if (decisionDefinitionType == null) {
      return null;
    }
    switch (decisionDefinitionType) {
      case DECISION_TABLE:
        return DecisionDefinitionTypeEnum.DECISION_TABLE;
      case LITERAL_EXPRESSION:
        return DecisionDefinitionTypeEnum.LITERAL_EXPRESSION;
      case UNSPECIFIED:
        return DecisionDefinitionTypeEnum.UNSPECIFIED;
      case UNKNOWN:
      default:
        return DecisionDefinitionTypeEnum.UNKNOWN;
    }
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
        .variableKey(variableEntity.key())
        .name(variableEntity.name())
        .value(variableEntity.value())
        .fullValue(variableEntity.fullValue())
        .processInstanceKey(variableEntity.processInstanceKey())
        .tenantId(variableEntity.tenantId())
        .isTruncated(variableEntity.isPreview())
        .scopeKey(variableEntity.scopeKey());
  }

  private record RuleIdentifier(String ruleId, int ruleIndex) {}
}
