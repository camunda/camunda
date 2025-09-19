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

import static io.camunda.client.api.search.request.SearchRequestBuilders.correlatedMessageSubscriptionFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.correlatedMessageSubscriptionSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.CorrelatedMessageSubscriptionFilter;
import io.camunda.client.api.search.request.CorrelatedMessageSubscriptionSearchRequest;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.CorrelatedMessageSubscription;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.CorrelatedMessageSubscriptionSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.CorrelatedMessageSubscriptionSearchQuery;
import io.camunda.client.protocol.rest.CorrelatedMessageSubscriptionSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class CorrelatedMessageSubscriptionSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<CorrelatedMessageSubscriptionSearchQuery>
    implements CorrelatedMessageSubscriptionSearchRequest {

  private final CorrelatedMessageSubscriptionSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CorrelatedMessageSubscriptionSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new CorrelatedMessageSubscriptionSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<CorrelatedMessageSubscription> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<CorrelatedMessageSubscription>> send() {
    final HttpCamundaFuture<SearchResponse<CorrelatedMessageSubscription>> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "correlated-message-subscriptions/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        CorrelatedMessageSubscriptionSearchQueryResult.class,
        SearchResponseMapper::toCorrelatedMessageSubscriptionSearchResponse,
        result);
    return result;
  }

  @Override
  public CorrelatedMessageSubscriptionSearchRequest filter(
      final CorrelatedMessageSubscriptionFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public CorrelatedMessageSubscriptionSearchRequest filter(
      final Consumer<CorrelatedMessageSubscriptionFilter> fn) {
    return filter(correlatedMessageSubscriptionFilter(fn));
  }

  @Override
  public CorrelatedMessageSubscriptionSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public CorrelatedMessageSubscriptionSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  public CorrelatedMessageSubscriptionSearchRequest sort(
      final CorrelatedMessageSubscriptionSort value) {
    request.setSort(
        SearchRequestSortMapper.toCorrelatedMessageSubscriptionSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public CorrelatedMessageSubscriptionSearchRequest sort(
      final Consumer<CorrelatedMessageSubscriptionSort> fn) {
    return sort(correlatedMessageSubscriptionSort(fn));
  }

  @Override
  protected CorrelatedMessageSubscriptionSearchQuery getSearchRequestProperty() {
    return request;
  }
}
