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
package io.camunda.zeebe.client.impl.search.query;

import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.decisionInstanceFilter;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.decisionInstanceSort;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.searchRequestPage;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.search.SearchRequestPage;
import io.camunda.zeebe.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.zeebe.client.api.search.query.DecisionInstanceQuery;
import io.camunda.zeebe.client.api.search.query.FinalSearchQueryStep;
import io.camunda.zeebe.client.api.search.response.DecisionInstance;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.client.api.search.sort.DecisionInstanceSort;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.search.SearchResponseMapper;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceFilterRequest;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceSearchQueryRequest;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceSearchQueryResponse;
import io.camunda.zeebe.client.protocol.rest.SearchQueryPageRequest;
import io.camunda.zeebe.client.protocol.rest.SearchQuerySortRequest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class DecisionInstanceQueryImpl
    extends TypedSearchRequestPropertyProvider<DecisionInstanceSearchQueryRequest>
    implements DecisionInstanceQuery {

  private final DecisionInstanceSearchQueryRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public DecisionInstanceQueryImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new DecisionInstanceSearchQueryRequest();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public DecisionInstanceQuery filter(final DecisionInstanceFilter value) {
    final DecisionInstanceFilterRequest filter = provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public DecisionInstanceQuery filter(final Consumer<DecisionInstanceFilter> fn) {
    return filter(decisionInstanceFilter(fn));
  }

  @Override
  public DecisionInstanceQuery sort(final DecisionInstanceSort value) {
    final List<SearchQuerySortRequest> sorting = provideSearchRequestProperty(value);
    request.setSort(sorting);
    return this;
  }

  @Override
  public DecisionInstanceQuery sort(final Consumer<DecisionInstanceSort> fn) {
    return sort(decisionInstanceSort(fn));
  }

  @Override
  public DecisionInstanceQuery page(final SearchRequestPage value) {
    final SearchQueryPageRequest page = provideSearchRequestProperty(value);
    request.setPage(page);
    return this;
  }

  @Override
  public DecisionInstanceQuery page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected DecisionInstanceSearchQueryRequest getSearchRequestProperty() {
    return request;
  }

  @Override
  public FinalSearchQueryStep<DecisionInstance> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<SearchQueryResponse<DecisionInstance>> send() {
    final HttpZeebeFuture<SearchQueryResponse<DecisionInstance>> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/decision-instances/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        DecisionInstanceSearchQueryResponse.class,
        resp -> SearchResponseMapper.toDecisionInstanceSearchResponse(resp, jsonMapper),
        result);
    return result;
  }
}
