/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceItem;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskItem;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryResponse;
import io.camunda.zeebe.util.Either;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ProblemDetail;

public final class SearchQueryResponseMapper {

  public SearchQueryResponseMapper() {}

  public static Either<ProblemDetail, ProcessInstanceSearchQueryResponse>
      toProcessInstanceSearchQueryResponse(final SearchQueryResult<ProcessInstanceEntity> result) {
    final var response = new ProcessInstanceSearchQueryResponse();
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
      response.setItems(toProcessInstances(items).get());
    }

    return Either.right(response);
  }

  public static Either<ProblemDetail, List<ProcessInstanceItem>> toProcessInstances(
      final List<ProcessInstanceEntity> instances) {
    return Either.right(
        instances.stream()
            .map(SearchQueryResponseMapper::toProcessInstance)
            .map(Either::get)
            .toList());
  }

  public static Either<ProblemDetail, ProcessInstanceItem> toProcessInstance(
      final ProcessInstanceEntity p) {
    return Either.right(
        new ProcessInstanceItem()
            .tenantId(p.tenantId())
            .key(p.key())
            .processVersion(p.processVersion())
            .bpmnProcessId(p.bpmnProcessId())
            .parentKey(p.parentKey())
            .parentFlowNodeInstanceKey(p.parentFlowNodeInstanceKey())
            .startDate(p.startDate())
            .endDate(p.endDate()));
  }

  public static Either<ProblemDetail, UserTaskSearchQueryResponse> toUserTaskSearchQueryResponse(
      final SearchQueryResult<UserTaskEntity> result) {
    final var response = new UserTaskSearchQueryResponse();
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
      response.setItems(toUserTasks(items).get());
    }

    return Either.right(response);
  }

  public static Either<ProblemDetail, List<UserTaskItem>> toUserTasks(
      final List<UserTaskEntity> tasks) {
    return Either.right(
        tasks.stream().map(SearchQueryResponseMapper::toUserTask).map(Either::get).toList());
  }

  public static Either<ProblemDetail, UserTaskItem> toUserTask(final UserTaskEntity t) {
    return Either.right(
        new UserTaskItem()
            .tenantIds(t.tenantId())
            .key(t.key())
            .processInstanceKey(t.processInstanceId())
            .processDefinitionKey(t.processDefinitionId())
            .taskState(t.state())
            .assignee(t.assignee())
            .candidateUser(t.candidateUsers())
            .candidateGroup(t.candidateGroups())
            .formKey(t.formKey())
            .taskDefinitionId(t.flowNodeBpmnId())
            .creationDate(t.creationTime())
            .completionDate(t.completionTime())
            .dueDate(t.dueDate())
            .followUpDate(t.followUpDate()));
  }
}
