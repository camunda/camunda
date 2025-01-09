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
package io.camunda.zeebe.client.impl.search;

import io.camunda.zeebe.client.api.search.response.DecisionDefinition;
import io.camunda.zeebe.client.api.search.response.DecisionRequirements;
import io.camunda.zeebe.client.api.search.response.FlowNodeInstance;
import io.camunda.zeebe.client.api.search.response.Incident;
import io.camunda.zeebe.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.client.api.search.response.SearchResponsePage;
import io.camunda.zeebe.client.api.search.response.UserTask;
import io.camunda.zeebe.client.impl.search.response.DecisionDefinitionImpl;
import io.camunda.zeebe.client.impl.search.response.DecisionRequirementsImpl;
import io.camunda.zeebe.client.impl.search.response.FlowNodeInstanceImpl;
import io.camunda.zeebe.client.impl.search.response.IncidentImpl;
import io.camunda.zeebe.client.impl.search.response.ProcessInstanceImpl;
import io.camunda.zeebe.client.impl.search.response.SearchQueryResponseImpl;
import io.camunda.zeebe.client.impl.search.response.SearchResponsePageImpl;
import io.camunda.zeebe.client.impl.search.response.UserTaskImpl;
import io.camunda.zeebe.client.protocol.rest.DecisionDefinitionSearchQueryResponse;
import io.camunda.zeebe.client.protocol.rest.DecisionRequirementsSearchQueryResponse;
import io.camunda.zeebe.client.protocol.rest.FlowNodeInstanceSearchQueryResponse;
import io.camunda.zeebe.client.protocol.rest.IncidentSearchQueryResponse;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceSearchQueryResponse;
import io.camunda.zeebe.client.protocol.rest.SearchQueryPageResponse;
import io.camunda.zeebe.client.protocol.rest.UserTaskSearchQueryResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SearchResponseMapper {

  private SearchResponseMapper() {}

  public static SearchQueryResponse<ProcessInstance> toProcessInstanceSearchResponse(
      final ProcessInstanceSearchQueryResponse response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<ProcessInstance> instances =
        toSearchResponseInstances(response.getItems(), ProcessInstanceImpl::new);

    return new SearchQueryResponseImpl<>(instances, page);
  }

  public static SearchQueryResponse<UserTask> toUserTaskSearchResponse(
      final UserTaskSearchQueryResponse response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<UserTask> instances =
        toSearchResponseInstances(response.getItems(), UserTaskImpl::new);
    return new SearchQueryResponseImpl<>(instances, page);
  }

  public static SearchQueryResponse<DecisionDefinition> toDecisionDefinitionSearchResponse(
      final DecisionDefinitionSearchQueryResponse response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<DecisionDefinition> instances =
        toSearchResponseInstances(response.getItems(), DecisionDefinitionImpl::new);
    return new SearchQueryResponseImpl<>(instances, page);
  }

  public static SearchQueryResponse<FlowNodeInstance> toFlowNodeInstanceSearchResponse(
      final FlowNodeInstanceSearchQueryResponse response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<FlowNodeInstance> instances =
        toSearchResponseInstances(response.getItems(), FlowNodeInstanceImpl::new);
    return new SearchQueryResponseImpl<>(instances, page);
  }

  public static SearchQueryResponse<Incident> toIncidentSearchResponse(
      final IncidentSearchQueryResponse response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<Incident> incidents =
        toSearchResponseInstances(response.getItems(), IncidentImpl::new);
    return new SearchQueryResponseImpl<>(incidents, page);
  }

  private static SearchResponsePage toSearchResponsePage(
      final SearchQueryPageResponse pageResponse) {
    return new SearchResponsePageImpl(
        pageResponse.getTotalItems(),
        pageResponse.getFirstSortValues(),
        pageResponse.getLastSortValues());
  }

  public static SearchQueryResponse<DecisionRequirements> toDecisionRequirementsSearchResponse(
      final DecisionRequirementsSearchQueryResponse response) {
    final SearchResponsePage page = toSearchResponsePage(response.getPage());
    final List<DecisionRequirements> instances =
        toSearchResponseInstances(response.getItems(), DecisionRequirementsImpl::new);
    return new SearchQueryResponseImpl<>(instances, page);
  }

  private static <T, R> List<R> toSearchResponseInstances(
      final List<T> items, final Function<T, R> mapper) {
    return Optional.ofNullable(items)
        .map(i -> i.stream().map(mapper).collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }
}
