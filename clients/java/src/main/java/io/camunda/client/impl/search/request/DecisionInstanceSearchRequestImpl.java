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

import static io.camunda.client.api.search.request.SearchRequestBuilders.decisionInstanceFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.decisionInstanceSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.request.DecisionInstanceSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.DecisionInstanceSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.DecisionInstanceSearchQueryResult;
import java.util.function.Consumer;

public class DecisionInstanceSearchRequestImpl
    extends AbstractSearchRequestImpl<
        io.camunda.client.protocol.rest.DecisionInstanceSearchQuery, DecisionInstance>
    implements DecisionInstanceSearchRequest {

  private final io.camunda.client.protocol.rest.DecisionInstanceSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;

  public DecisionInstanceSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    request = new io.camunda.client.protocol.rest.DecisionInstanceSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
  }

  @Override
  public DecisionInstanceSearchRequest filter(final DecisionInstanceFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public DecisionInstanceSearchRequest filter(final Consumer<DecisionInstanceFilter> fn) {
    return filter(decisionInstanceFilter(fn));
  }

  @Override
  public DecisionInstanceSearchRequest sort(final DecisionInstanceSort value) {
    request.setSort(
        SearchRequestSortMapper.toDecisionInstanceSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public DecisionInstanceSearchRequest sort(final Consumer<DecisionInstanceSort> fn) {
    return sort(decisionInstanceSort(fn));
  }

  @Override
  public DecisionInstanceSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public DecisionInstanceSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected io.camunda.client.protocol.rest.DecisionInstanceSearchQuery getSearchRequestProperty() {
    return request;
  }

  @Override
  public CamundaFuture<SearchResponse<DecisionInstance>> send() {
    return httpClient.post(
        "/decision-instances/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        DecisionInstanceSearchQueryResult.class,
        resp -> SearchResponseMapper.toDecisionInstanceSearchResponse(resp, jsonMapper),
        consistencyPolicy);
  }
}
