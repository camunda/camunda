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
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionItem;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceItem;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageResponse;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ProblemDetail;

public final class SearchQueryResponseMapper {

  private SearchQueryResponseMapper() {}

  public static Either<ProblemDetail, ProcessInstanceSearchQueryResponse>
      toProcessInstanceSearchQueryResponse(final SearchQueryResult<ProcessInstanceEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    final var response =
        new ProcessInstanceSearchQueryResponse()
            .page(page)
            .items(
                ofNullable(result.items())
                    .map(SearchQueryResponseMapper::toProcessInstances)
                    .map(Either::get)
                    .orElseGet(ArrayList::new));

    return Either.right(response);
  }

  public static Either<ProblemDetail, DecisionDefinitionSearchQueryResponse>
      toDecisionDefinitionSearchQueryResponse(
          final SearchQueryResult<DecisionDefinitionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    final var response =
        new DecisionDefinitionSearchQueryResponse()
            .page(page)
            .items(
                ofNullable(result.items())
                    .map(SearchQueryResponseMapper::toDecisionDefinitions)
                    .map(Either::get)
                    .orElseGet(ArrayList::new));

    return Either.right(response);
  }

  private static SearchQueryPageResponse toSearchQueryPageResponse(
      final SearchQueryResult<?> result) {
    return new SearchQueryPageResponse()
        .totalItems(result.total())
        .lastSortValues(
            ofNullable(result.sortValues()).map(Arrays::asList).orElseGet(ArrayList::new));
  }

  private static Either<ProblemDetail, List<ProcessInstanceItem>> toProcessInstances(
      final List<ProcessInstanceEntity> instances) {
    return Either.right(
        instances.stream()
            .map(SearchQueryResponseMapper::toProcessInstance)
            .map(Either::get)
            .toList());
  }

  private static Either<ProblemDetail, ProcessInstanceItem> toProcessInstance(
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

  private static Either<ProblemDetail, List<DecisionDefinitionItem>> toDecisionDefinitions(
      final List<DecisionDefinitionEntity> instances) {
    return Either.right(
        instances.stream()
            .map(SearchQueryResponseMapper::toDecisionDefinition)
            .map(Either::get)
            .toList());
  }

  private static Either<ProblemDetail, DecisionDefinitionItem> toDecisionDefinition(
      final DecisionDefinitionEntity d) {
    return Either.right(
        new DecisionDefinitionItem()
            .tenantId(d.tenantId())
            .key(d.key())
            .id(d.id())
            .name(d.name())
            .version(d.version())
            .decisionId(d.decisionId())
            .decisionRequirementsKey(d.decisionRequirementsKey())
            .decisionRequirementsId(d.decisionRequirementsId())
            .decisionRequirementsName(d.decisionRequirementsName())
            .decisionRequirementsVersion(d.decisionRequirementsVersion()));
  }
}
