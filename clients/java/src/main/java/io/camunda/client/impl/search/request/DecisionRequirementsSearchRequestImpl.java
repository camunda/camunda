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
package io.camunda.client.impl.search.request;

import static io.camunda.client.api.search.request.SearchRequestBuilders.decisionRequirementsSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.DecisionRequirementsFilter;
import io.camunda.client.api.search.request.DecisionRequirementsSearchRequest;
import io.camunda.client.api.search.request.SearchRequestBuilders;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.DecisionRequirements;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.DecisionRequirementsSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.DecisionRequirementsSearchQuery;
import io.camunda.client.protocol.rest.DecisionRequirementsSearchQueryResult;
import java.util.function.Consumer;

public class DecisionRequirementsSearchRequestImpl
    extends AbstractSearchRequestImpl<DecisionRequirementsSearchQuery, DecisionRequirements>
    implements DecisionRequirementsSearchRequest {

  private final DecisionRequirementsSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;

  public DecisionRequirementsSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    request = new DecisionRequirementsSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
  }

  @Override
  public HttpCamundaFuture<SearchResponse<DecisionRequirements>> send() {
    final HttpCamundaFuture<SearchResponse<DecisionRequirements>> result =
        new HttpCamundaFuture<>();
    return httpClient.post(
        "/decision-requirements/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        DecisionRequirementsSearchQueryResult.class,
        SearchResponseMapper::toDecisionRequirementsSearchResponse,
        consistencyPolicy);
  }

  @Override
  public DecisionRequirementsSearchRequest filter(final DecisionRequirementsFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public DecisionRequirementsSearchRequest filter(final Consumer<DecisionRequirementsFilter> fn) {
    return filter(SearchRequestBuilders.decisionRequirementsFilter(fn));
  }

  @Override
  public DecisionRequirementsSearchRequest sort(final DecisionRequirementsSort value) {
    request.setSort(
        SearchRequestSortMapper.toDecisionRequirementsSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public DecisionRequirementsSearchRequest sort(final Consumer<DecisionRequirementsSort> fn) {
    return sort(decisionRequirementsSort(fn));
  }

  @Override
  public DecisionRequirementsSearchRequest page(final SearchRequestPage value) {
    final SearchRequestPageImpl page = (SearchRequestPageImpl) value;
    request.setPage(page.getSearchRequestProperty());
    return this;
  }

  @Override
  public DecisionRequirementsSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected DecisionRequirementsSearchQuery getSearchRequestProperty() {
    return request;
  }
}
