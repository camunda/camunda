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

import static io.camunda.client.api.search.request.SearchRequestBuilders.anyPage;
import static io.camunda.client.api.search.request.SearchRequestBuilders.globalExecutionListenerFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.globalExecutionListenerSort;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.GlobalExecutionListenerFilter;
import io.camunda.client.api.search.page.AnyPage;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.GlobalExecutionListenerSearchRequest;
import io.camunda.client.api.search.response.GlobalExecutionListener;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.GlobalExecutionListenerSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.GlobalExecutionListenerSearchQueryRequest;
import io.camunda.client.protocol.rest.GlobalExecutionListenerSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class GlobalExecutionListenerSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<GlobalExecutionListenerSearchQueryRequest>
    implements GlobalExecutionListenerSearchRequest {

  private final GlobalExecutionListenerSearchQueryRequest request;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public GlobalExecutionListenerSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new GlobalExecutionListenerSearchQueryRequest();
  }

  @Override
  public FinalSearchRequestStep<GlobalExecutionListener> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<GlobalExecutionListener>> send() {
    final HttpCamundaFuture<SearchResponse<GlobalExecutionListener>> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/global-execution-listeners/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        GlobalExecutionListenerSearchQueryResult.class,
        SearchResponseMapper::toGlobalExecutionListenerSearchResponse,
        result);
    return result;
  }

  @Override
  public GlobalExecutionListenerSearchRequest filter(final GlobalExecutionListenerFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public GlobalExecutionListenerSearchRequest filter(
      final Consumer<GlobalExecutionListenerFilter> fn) {
    return filter(globalExecutionListenerFilter(fn));
  }

  @Override
  public GlobalExecutionListenerSearchRequest page(final AnyPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public GlobalExecutionListenerSearchRequest page(final Consumer<AnyPage> fn) {
    return page(anyPage(fn));
  }

  @Override
  public GlobalExecutionListenerSearchRequest sort(final GlobalExecutionListenerSort value) {
    request.setSort(
        SearchRequestSortMapper.toGlobalExecutionListenerSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public GlobalExecutionListenerSearchRequest sort(final Consumer<GlobalExecutionListenerSort> fn) {
    return sort(globalExecutionListenerSort(fn));
  }

  @Override
  protected GlobalExecutionListenerSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
