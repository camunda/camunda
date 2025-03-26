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

import static io.camunda.client.api.search.SearchRequestBuilders.flowNodeInstanceFilter;
import static io.camunda.client.api.search.SearchRequestBuilders.flowNodeInstanceSort;
import static io.camunda.client.api.search.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.SearchRequestPage;
import io.camunda.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.FlownodeInstanceSearchRequest;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.SearchQueryResponse;
import io.camunda.client.api.search.sort.FlownodeInstanceSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.SearchQuerySortRequestMapper;
import io.camunda.client.impl.search.SearchRequestPageImpl;
import io.camunda.client.impl.search.SearchResponseMapper;
import io.camunda.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.search.sort.FlownodeInstanceSortImpl;
import io.camunda.client.protocol.rest.FlowNodeInstanceFilter;
import io.camunda.client.protocol.rest.FlowNodeInstanceSearchQuery;
import io.camunda.client.protocol.rest.FlowNodeInstanceSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class FlowNodeInstanceSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<FlowNodeInstanceSearchQuery>
    implements FlownodeInstanceSearchRequest {

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final FlowNodeInstanceSearchQuery request;
  private final RequestConfig.Builder httpRequestConfig;

  public FlowNodeInstanceSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new FlowNodeInstanceSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<FlowNodeInstance> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchQueryResponse<FlowNodeInstance>> send() {
    final HttpCamundaFuture<SearchQueryResponse<FlowNodeInstance>> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/flownode-instances/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        FlowNodeInstanceSearchQueryResult.class,
        SearchResponseMapper::toFlowNodeInstanceSearchResponse,
        result);
    return result;
  }

  @Override
  public FlownodeInstanceSearchRequest filter(final FlownodeInstanceFilter value) {
    final FlowNodeInstanceFilter filter = provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public FlownodeInstanceSearchRequest filter(final Consumer<FlownodeInstanceFilter> fn) {
    return filter(flowNodeInstanceFilter(fn));
  }

  @Override
  public FlownodeInstanceSearchRequest sort(final FlownodeInstanceSort value) {
    final FlownodeInstanceSortImpl sorting = (FlownodeInstanceSortImpl) value;
    request.setSort(
        SearchQuerySortRequestMapper.toFlowNodeInstanceSearchQuerySortRequest(
            sorting.getSearchRequestProperty()));
    return this;
  }

  @Override
  public FlownodeInstanceSearchRequest sort(final Consumer<FlownodeInstanceSort> fn) {
    return sort(flowNodeInstanceSort(fn));
  }

  @Override
  public FlownodeInstanceSearchRequest page(final SearchRequestPage value) {
    final SearchRequestPageImpl page = (SearchRequestPageImpl) value;
    request.setPage(page.getSearchRequestProperty());
    return this;
  }

  @Override
  public FlownodeInstanceSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected FlowNodeInstanceSearchQuery getSearchRequestProperty() {
    return request;
  }
}
