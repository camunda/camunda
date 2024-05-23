/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.query.SearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstance;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResponse;
import io.camunda.zeebe.util.Either;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ProblemDetail;

public class SearchQueryResponseMapper {

  public static Either<ProblemDetail, ProcessInstanceSearchQueryResponse>
      toProcessInstanceSearchQueryResponse(final SearchQueryResult<ProcessInstanceEntity> result) {
    final var response = new ProcessInstanceSearchQueryResponse();
    final var total = result.total();
    final var sortValues = result.sortValues();
    final var items = result.items();

    response.setTotal(total);

    if (sortValues != null) {
      response.setSortValues(Arrays.asList(sortValues));
    }

    if (items != null) {
      response.setItems(toProcessInstances(items).get());
    }

    return Either.right(response);
  }

  public static Either<ProblemDetail, List<ProcessInstance>> toProcessInstances(
      final List<ProcessInstanceEntity> instances) {
    return Either.right(
        instances.stream()
            .map(SearchQueryResponseMapper::toProcessInstance)
            .map(Either::get)
            .toList());
  }

  public static Either<ProblemDetail, ProcessInstance> toProcessInstance(
      final ProcessInstanceEntity p) {
    return Either.right(
        new ProcessInstance()
            .tenantId(p.getTenantId())
            .key(p.getKey())
            .processVersion(p.getProcessVersion())
            .bpmnProcessId(p.getBpmnProcessId())
            .parentKey(p.getParentKey())
            .parentFlowNodeInstanceKey(p.getParentFlowNodeInstanceKey())
            .startDate(p.getStartDate())
            .endDate(p.getEndDate()));
  }
}
