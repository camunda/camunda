/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client.impl.search.response;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionRequirements;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.SearchResponsePage;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.protocol.rest.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SearchResponseMapper {

  private SearchResponseMapper() {}

  public static SearchResponse<ProcessDefinition> toProcessDefinitionSearchResponse(
      final ProcessDefinitionSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<ProcessDefinition> instances =
        toSearchResponseInstances(response.getItems(), ProcessDefinitionImpl::new);

    return new SearchResponseImpl<>(instances, page);
  }

  public static ProcessDefinition toProcessDefinitionGetResponse(
      final ProcessDefinitionResult response) {
    return new ProcessDefinitionImpl(response);
  }

  public static ProcessInstance toProcessInstanceGetResponse(final ProcessInstanceResult response) {
    return new ProcessInstanceImpl(response);
  }

  public static SearchResponse<ProcessInstance> toProcessInstanceSearchResponse(
      final ProcessInstanceSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<ProcessInstance> instances =
        toSearchResponseInstances(response.getItems(), ProcessInstanceImpl::new);

    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<UserTask> toUserTaskSearchResponse(
      final UserTaskSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<UserTask> instances =
        toSearchResponseInstances(response.getItems(), UserTaskImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<Variable> toVariableSearchResponse(
      final VariableSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Variable> instances =
        toSearchResponseInstances(response.getItems(), VariableImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<DecisionDefinition> toDecisionDefinitionSearchResponse(
      final DecisionDefinitionSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<DecisionDefinition> instances =
        toSearchResponseInstances(response.getItems(), DecisionDefinitionImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<FlowNodeInstance> toFlowNodeInstanceSearchResponse(
      final FlowNodeInstanceSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<FlowNodeInstance> instances =
        toSearchResponseInstances(response.getItems(), FlowNodeInstanceImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }

  public static FlowNodeInstance toFlowNodeInstanceGetResponse(
      final FlowNodeInstanceResult response) {
    return new FlowNodeInstanceImpl(response);
  }

  public static SearchResponse<Incident> toIncidentSearchResponse(
      final IncidentSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Incident> incidents =
        toSearchResponseInstances(response.getItems(), IncidentImpl::new);
    return new SearchResponseImpl<>(incidents, page);
  }

  public static Incident toIncidentGetResponse(final IncidentResult response) {
    return new IncidentImpl(response);
  }

  private static SearchResponsePage toSearchResponsePage(
      final SearchQueryPageResponse pageResponse) {
    return new SearchResponsePageImpl(
        pageResponse.getTotalItems(),
        pageResponse.getFirstSortValues(),
        pageResponse.getLastSortValues());
  }

  public static SearchResponse<DecisionRequirements> toDecisionRequirementsSearchResponse(
      final DecisionRequirementsSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<DecisionRequirements> instances =
        toSearchResponseInstances(response.getItems(), DecisionRequirementsImpl::new);
    return new SearchResponseImpl<>(instances, page);
  }

  public static SearchResponse<DecisionInstance> toDecisionInstanceSearchResponse(
      final DecisionInstanceSearchQueryResult response, final JsonMapper jsonMapper) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<DecisionInstance> instances =
        toSearchResponseInstances(
            response.getItems(), item -> new DecisionInstanceImpl(item, jsonMapper));
    return new SearchResponseImpl<>(instances, page);
  }

  private static <T, R> List<R> toSearchResponseInstances(
      final List<T> items, final Function<T, R> mapper) {
    return Optional.ofNullable(items)
        .map(i -> i.stream().map(mapper).collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }
}
