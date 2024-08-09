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
import io.camunda.service.entities.DecisionRequirementsEntity;
import io.camunda.service.entities.IncidentEntity;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionItem;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsItem;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.IncidentItem;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQueryResponse;
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

  private static List<DecisionRequirementsItem> toDecisionRequirements(
      final List<DecisionRequirementsEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionRequirements).toList();
  }

  private static DecisionDefinitionItem toDecisionDefinition(final DecisionDefinitionEntity d) {
    return new DecisionDefinitionItem()
        .tenantId(d.tenantId())
        .decisionKey(d.key())
        .dmnDecisionName(d.name())
        .version(d.version())
        .dmnDecisionId(d.decisionId())
        .decisionRequirementsKey(d.decisionRequirementsKey())
        .dmnDecisionRequirementsId(d.decisionRequirementsId());
  }

  private static DecisionRequirementsItem toDecisionRequirements(
      final DecisionRequirementsEntity d) {
    return new DecisionRequirementsItem()
        .tenantId(d.tenantId())
        .decisionRequirementsKey(d.key())
        .dmnDecisionRequirementsName(d.name())
        .version(d.version())
        .resourceName(d.resourceName())
        .dmnDecisionRequirementsId(d.decisionRequirementsId());
  }

  private static List<UserTaskItem> toUserTasks(final List<UserTaskEntity> tasks) {
    return tasks.stream().map(SearchQueryResponseMapper::toUserTask).toList();
  }

  private static List<IncidentItem> toIncidents(final List<IncidentEntity> incidents) {
    return incidents.stream().map(SearchQueryResponseMapper::toIncident).toList();
  }

  private static IncidentItem toIncident(final IncidentEntity t) {
    return new IncidentItem()
        .key(t.key())
        .processDefinitionKey(t.processDefinitionKey())
        .processInstanceKey(t.processInstanceKey())
        .type(t.type())
        .flowNodeId(t.flowNodeId())
        .flowNodeInstanceId(t.flowNodeInstanceId())
        .creationTime(t.creationTime())
        .state(t.state())
        .jobKey(t.jobKey())
        .tenantId(t.tenantId())
        .hasActiveOperation(t.hasActiveOperation())
        .lastOperation(null /*new OperationItem()*/)
        .rootCauseInstance(null /*new ProcessInstanceReferenceItem()*/)
        .rootCauseDecision(null /*new DecisionInstanceReferenceItem()*/);
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
