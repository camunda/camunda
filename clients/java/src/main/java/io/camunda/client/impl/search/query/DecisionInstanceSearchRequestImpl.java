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
package io.camunda.client.impl.search.query;

import static io.camunda.client.api.search.SearchRequestBuilders.decisionInstanceFilter;
import static io.camunda.client.api.search.SearchRequestBuilders.decisionInstanceSort;
import static io.camunda.client.api.search.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.SearchRequestPage;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.request.DecisionInstanceSearchRequest;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.SearchQueryResponse;
import io.camunda.client.api.search.sort.DecisionInstanceSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.SearchQuerySortRequest;
import io.camunda.client.impl.search.SearchQuerySortRequestMapper;
import io.camunda.client.impl.search.SearchResponseMapper;
import io.camunda.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.DecisionInstanceSearchQueryResult;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class DecisionInstanceSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.DecisionInstanceSearchQuery>
    implements DecisionInstanceSearchRequest {

  private final io.camunda.client.protocol.rest.DecisionInstanceSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public DecisionInstanceSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new io.camunda.client.protocol.rest.DecisionInstanceSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public DecisionInstanceSearchRequest filter(final DecisionInstanceFilter value) {
    final io.camunda.client.protocol.rest.DecisionInstanceFilter filter =
        provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public DecisionInstanceSearchRequest filter(final Consumer<DecisionInstanceFilter> fn) {
    return filter(decisionInstanceFilter(fn));
  }

  @Override
  public DecisionInstanceSearchRequest sort(final DecisionInstanceSort value) {
    final List<SearchQuerySortRequest> sorting = provideSearchRequestProperty(value);
    request.setSort(SearchQuerySortRequestMapper.toDecisionInstanceSearchQuerySortRequest(sorting));
    return this;
  }

  @Override
  public DecisionInstanceSearchRequest sort(final Consumer<DecisionInstanceSort> fn) {
    return sort(decisionInstanceSort(fn));
  }

  @Override
  public DecisionInstanceSearchRequest page(final SearchRequestPage value) {
    final SearchQueryPageRequest page = provideSearchRequestProperty(value);
    request.setPage(page);
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
  public FinalSearchRequestStep<DecisionInstance> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchQueryResponse<DecisionInstance>> send() {
    final HttpCamundaFuture<SearchQueryResponse<DecisionInstance>> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/decision-instances/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        DecisionInstanceSearchQueryResult.class,
        resp -> SearchResponseMapper.toDecisionInstanceSearchResponse(resp, jsonMapper),
        result);
    return result;
  }
}
