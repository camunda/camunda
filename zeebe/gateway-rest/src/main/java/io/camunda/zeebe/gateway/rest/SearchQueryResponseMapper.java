/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static java.util.Optional.ofNullable;

import io.camunda.service.entities.DecisionDefinitionEntity;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionItem;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceItem;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskItem;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryResponse;
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

  private static ProcessInstanceItem toProcessInstance(final ProcessInstanceEntity p) {
    return new ProcessInstanceItem()
        .tenantId(p.tenantId())
        .key(p.key())
        .processVersion(p.processVersion())
        .bpmnProcessId(p.bpmnProcessId())
        .parentKey(p.parentKey())
        .parentFlowNodeInstanceKey(p.parentFlowNodeInstanceKey())
        .startDate(p.startDate())
        .endDate(p.endDate());
  }

  private static List<DecisionDefinitionItem> toDecisionDefinitions(
      final List<DecisionDefinitionEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionDefinition).toList();
  }

  private static DecisionDefinitionItem toDecisionDefinition(final DecisionDefinitionEntity d) {
    return new DecisionDefinitionItem()
        .tenantId(d.tenantId())
        .key(d.key())
        .id(d.id())
        .name(d.name())
        .version(d.version())
        .decisionId(d.decisionId())
        .decisionRequirementsKey(d.decisionRequirementsKey())
        .decisionRequirementsId(d.decisionRequirementsId())
        .decisionRequirementsName(d.decisionRequirementsName())
        .decisionRequirementsVersion(d.decisionRequirementsVersion());
  }

  private static List<UserTaskItem> toUserTasks(final List<UserTaskEntity> tasks) {
    return tasks.stream().map(SearchQueryResponseMapper::toUserTask).toList();
  }

  private static UserTaskItem toUserTask(final UserTaskEntity t) {
    return new UserTaskItem()
        .tenantIds(t.tenantId())
        .key(t.key())
        .processInstanceKey(t.processInstanceId())
        .processDefinitionKey(t.processDefinitionId())
        .elementInstanceKey(t.flowNodeInstanceId())
        .bpmnProcessId(t.bpmnProcessId())
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
        .customHeaders(t.customHeaders());
  }
}
