/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.search;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionRequirements;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchQueryResponse;
import io.camunda.client.api.search.response.SearchResponsePage;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.impl.search.response.DecisionDefinitionImpl;
import io.camunda.client.impl.search.response.DecisionInstanceImpl;
import io.camunda.client.impl.search.response.DecisionRequirementsImpl;
import io.camunda.client.impl.search.response.FlowNodeInstanceImpl;
import io.camunda.client.impl.search.response.IncidentImpl;
import io.camunda.client.impl.search.response.ProcessDefinitionImpl;
import io.camunda.client.impl.search.response.ProcessInstanceImpl;
import io.camunda.client.impl.search.response.SearchQueryResponseImpl;
import io.camunda.client.impl.search.response.SearchResponsePageImpl;
import io.camunda.client.impl.search.response.UserTaskImpl;
import io.camunda.client.impl.search.response.VariableImpl;
import io.camunda.client.protocol.rest.DecisionDefinitionSearchQueryResult;
import io.camunda.client.protocol.rest.DecisionInstanceSearchQueryResult;
import io.camunda.client.protocol.rest.DecisionRequirementsSearchQueryResult;
import io.camunda.client.protocol.rest.FlowNodeInstanceResult;
import io.camunda.client.protocol.rest.FlowNodeInstanceSearchQueryResult;
import io.camunda.client.protocol.rest.IncidentResult;
import io.camunda.client.protocol.rest.IncidentSearchQueryResult;
import io.camunda.client.protocol.rest.ProcessDefinitionResult;
import io.camunda.client.protocol.rest.ProcessDefinitionSearchQueryResult;
import io.camunda.client.protocol.rest.ProcessInstanceResult;
import io.camunda.client.protocol.rest.ProcessInstanceSearchQueryResult;
import io.camunda.client.protocol.rest.SearchQueryPageResponse;
import io.camunda.client.protocol.rest.UserTaskSearchQueryResult;
import io.camunda.client.protocol.rest.VariableSearchQueryResult;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SearchResponseMapper {

  private SearchResponseMapper() {
  }

  public static SearchQueryResponse<ProcessDefinition> toProcessDefinitionSearchResponse(
      final ProcessDefinitionSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<ProcessDefinition> instances =
        toSearchResponseInstances(response.getItems(), ProcessDefinitionImpl::new);

    return new SearchQueryResponseImpl<>(instances, page);
  }

  public static ProcessDefinition toProcessDefinitionGetResponse(
      final ProcessDefinitionResult response) {
    return new ProcessDefinitionImpl(response);
  }

  public static ProcessInstance toProcessInstanceGetResponse(final ProcessInstanceResult response) {
    return new ProcessInstanceImpl(response);
  }

  public static SearchQueryResponse<ProcessInstance> toProcessInstanceSearchResponse(
      final ProcessInstanceSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<ProcessInstance> instances =
        toSearchResponseInstances(response.getItems(), ProcessInstanceImpl::new);

    return new SearchQueryResponseImpl<>(instances, page);
  }

  public static SearchQueryResponse<UserTask> toUserTaskSearchResponse(
      final UserTaskSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<UserTask> instances =
        toSearchResponseInstances(response.getItems(), UserTaskImpl::new);
    return new SearchQueryResponseImpl<>(instances, page);
  }

  public static SearchQueryResponse<Variable> toVariableSearchResponse(
      final VariableSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Variable> instances =
        toSearchResponseInstances(response.getItems(), VariableImpl::new);
    return new SearchQueryResponseImpl<>(instances, page);
  }

  public static SearchQueryResponse<DecisionDefinition> toDecisionDefinitionSearchResponse(
      final DecisionDefinitionSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<DecisionDefinition> instances =
        toSearchResponseInstances(response.getItems(), DecisionDefinitionImpl::new);
    return new SearchQueryResponseImpl<>(instances, page);
  }

  public static SearchQueryResponse<FlowNodeInstance> toFlowNodeInstanceSearchResponse(
      final FlowNodeInstanceSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<FlowNodeInstance> instances =
        toSearchResponseInstances(response.getItems(), FlowNodeInstanceImpl::new);
    return new SearchQueryResponseImpl<>(instances, page);
  }

  public static FlowNodeInstance toFlowNodeInstanceGetResponse(
      final FlowNodeInstanceResult response) {
    return new FlowNodeInstanceImpl(response);
  }

  public static SearchQueryResponse<Incident> toIncidentSearchResponse(
      final IncidentSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Incident> incidents =
        toSearchResponseInstances(response.getItems(), IncidentImpl::new);
    return new SearchQueryResponseImpl<>(incidents, page);
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

  public static SearchQueryResponse<DecisionRequirements> toDecisionRequirementsSearchResponse(
      final DecisionRequirementsSearchQueryResult response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<DecisionRequirements> instances =
        toSearchResponseInstances(response.getItems(), DecisionRequirementsImpl::new);
    return new SearchQueryResponseImpl<>(instances, page);
  }

  public static SearchQueryResponse<DecisionInstance> toDecisionInstanceSearchResponse(
      final DecisionInstanceSearchQueryResult response, final JsonMapper jsonMapper) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<DecisionInstance> instances =
        toSearchResponseInstances(
            response.getItems(), item -> new DecisionInstanceImpl(item, jsonMapper));
    return new SearchQueryResponseImpl<>(instances, page);
  }

  private static <T, R> List<R> toSearchResponseInstances(
      final List<T> items, final Function<T, R> mapper) {
    return Optional.ofNullable(items)
        .map(i -> i.stream().map(mapper).collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }
}
