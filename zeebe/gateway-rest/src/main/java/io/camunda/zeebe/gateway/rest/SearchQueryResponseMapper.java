/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static java.util.Optional.ofNullable;

import io.camunda.service.entities.*;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.*;
import io.camunda.zeebe.util.Either;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SearchQueryResponseMapper {

  private SearchQueryResponseMapper() {}

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
        .incident(p.incident())
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
        .incident(instance.incident())
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
        .decisionDefinitionName(d.name())
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
        .decisionRequirementsName(d.name())
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
        .key(t.key())
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
        .key(Long.valueOf(f.id()))
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

  private static DecisionInstanceItem toDecisionInstance(final DecisionInstanceEntity entity) {
    return new DecisionInstanceItem()
        .decisionInstanceKey(entity.key())
        .state(
            (entity.state() == null)
                ? null
                : DecisionInstanceStateEnum.fromValue(entity.state().name()))
        .evaluationDate(entity.evaluationDate())
        .evaluationFailure(entity.evaluationFailure())
        .processDefinitionKey(entity.processDefinitionKey())
        .processInstanceKey(entity.processInstanceKey())
        .decisionDefinitionKey(Long.valueOf(entity.decisionDefinitionId()))
        .decisionDefinitionId(entity.decisionId())
        .decisionDefinitionName(entity.decisionName())
        .decisionDefinitionVersion(entity.decisionVersion())
        .decisionDefinitionType(
            (entity.decisionType() == null)
                ? null
                : DecisionInstanceTypeEnum.fromValue(entity.decisionType().name()))
        .result(entity.result());
  }
}
