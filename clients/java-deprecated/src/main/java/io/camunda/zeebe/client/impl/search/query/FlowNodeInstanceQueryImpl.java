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

import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.flowNodeInstanceFilter;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.flowNodeInstanceSort;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.searchRequestPage;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.search.SearchRequestPage;
import io.camunda.zeebe.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.zeebe.client.api.search.query.FinalSearchQueryStep;
import io.camunda.zeebe.client.api.search.query.FlownodeInstanceQuery;
import io.camunda.zeebe.client.api.search.response.FlowNodeInstance;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.client.api.search.sort.FlownodeInstanceSort;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.search.SearchRequestPageImpl;
import io.camunda.zeebe.client.impl.search.SearchResponseMapper;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.impl.search.sort.FlownodeInstanceSortImpl;
import io.camunda.zeebe.client.protocol.rest.ElementInstanceSearchQuery;
import io.camunda.zeebe.client.protocol.rest.ElementInstanceSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by the new Camunda Client Java. Please see
 *     the <a
 *     href="https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/">Camunda
 *     Java Client migration guide</a>
 */
@Deprecated
public class FlowNodeInstanceQueryImpl
    extends TypedSearchRequestPropertyProvider<ElementInstanceSearchQuery>
    implements FlownodeInstanceQuery {

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final ElementInstanceSearchQuery request;
  private final RequestConfig.Builder httpRequestConfig;

  public FlowNodeInstanceQueryImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new ElementInstanceSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchQueryStep<FlowNodeInstance> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<SearchQueryResponse<FlowNodeInstance>> send() {
    final HttpZeebeFuture<SearchQueryResponse<FlowNodeInstance>> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/flownode-instances/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ElementInstanceSearchQueryResult.class,
        SearchResponseMapper::toFlowNodeInstanceSearchResponse,
        result);
    return result;
  }

  @Override
  public FlownodeInstanceQuery filter(final FlownodeInstanceFilter value) {
    final io.camunda.zeebe.client.protocol.rest.ElementInstanceFilter filter =
        provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public FlownodeInstanceQuery filter(final Consumer<FlownodeInstanceFilter> fn) {
    return filter(flowNodeInstanceFilter(fn));
  }

  @Override
  public FlownodeInstanceQuery sort(final FlownodeInstanceSort value) {
    final FlownodeInstanceSortImpl sorting = (FlownodeInstanceSortImpl) value;
    request.setSort(sorting.getSearchRequestProperty());
    return this;
  }

  @Override
  public FlownodeInstanceQuery sort(final Consumer<FlownodeInstanceSort> fn) {
    return sort(flowNodeInstanceSort(fn));
  }

  @Override
  public FlownodeInstanceQuery page(final SearchRequestPage value) {
    final SearchRequestPageImpl page = (SearchRequestPageImpl) value;
    request.setPage(page.getSearchRequestProperty());
    return this;
  }

  @Override
  public FlownodeInstanceQuery page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected ElementInstanceSearchQuery getSearchRequestProperty() {
    return request;
  }
}
