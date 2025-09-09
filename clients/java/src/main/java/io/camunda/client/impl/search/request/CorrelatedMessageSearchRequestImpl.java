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

import static io.camunda.client.api.search.request.SearchRequestBuilders.correlatedMessageFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.correlatedMessageSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.CorrelatedMessageFilter;
import io.camunda.client.api.search.request.CorrelatedMessageSearchRequest;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.CorrelatedMessage;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.CorrelatedMessageSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.CorrelatedMessageSearchQuery;
import io.camunda.client.protocol.rest.CorrelatedMessageSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class CorrelatedMessageSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<CorrelatedMessageSearchQuery>
    implements CorrelatedMessageSearchRequest {

  private final CorrelatedMessageSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CorrelatedMessageSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new CorrelatedMessageSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<CorrelatedMessage> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<CorrelatedMessage>> send() {
    final HttpCamundaFuture<SearchResponse<CorrelatedMessage>> result = new HttpCamundaFuture<>();
    httpClient.post(
        "correlated-messages/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        CorrelatedMessageSearchQueryResult.class,
        SearchResponseMapper::toCorrelatedMessageSearchResponse,
        result);
    return result;
  }

  @Override
  public CorrelatedMessageSearchRequest filter(final CorrelatedMessageFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public CorrelatedMessageSearchRequest filter(final Consumer<CorrelatedMessageFilter> fn) {
    return filter(correlatedMessageFilter(fn));
  }

  @Override
  public CorrelatedMessageSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public CorrelatedMessageSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  public CorrelatedMessageSearchRequest sort(final CorrelatedMessageSort value) {
    request.setSort(
        SearchRequestSortMapper.toCorrelatedMessageSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public CorrelatedMessageSearchRequest sort(final Consumer<CorrelatedMessageSort> fn) {
    return sort(correlatedMessageSort(fn));
  }

  @Override
  protected CorrelatedMessageSearchQuery getSearchRequestProperty() {
    return request;
  }
}