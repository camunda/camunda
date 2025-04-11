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

import static io.camunda.client.api.search.request.SearchRequestBuilders.elementInstanceFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.elementInstanceSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.request.ElementInstanceSearchRequest;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.ElementInstanceSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.impl.search.sort.ElementInstanceSortImpl;
import io.camunda.client.protocol.rest.ElementInstanceSearchQuery;
import io.camunda.client.protocol.rest.ElementInstanceSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ElementInstanceSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<ElementInstanceSearchQuery>
    implements ElementInstanceSearchRequest {

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final ElementInstanceSearchQuery request;
  private final RequestConfig.Builder httpRequestConfig;

  public ElementInstanceSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new ElementInstanceSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<ElementInstance> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<ElementInstance>> send() {
    final HttpCamundaFuture<SearchResponse<ElementInstance>> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/element-instances/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ElementInstanceSearchQueryResult.class,
        SearchResponseMapper::toElementInstanceSearchResponse,
        result);
    return result;
  }

  @Override
  public ElementInstanceSearchRequest filter(final ElementInstanceFilter value) {
    final io.camunda.client.protocol.rest.ElementInstanceFilter filter =
        provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public ElementInstanceSearchRequest filter(final Consumer<ElementInstanceFilter> fn) {
    return filter(elementInstanceFilter(fn));
  }

  @Override
  public ElementInstanceSearchRequest sort(final ElementInstanceSort value) {
    final ElementInstanceSortImpl sorting = (ElementInstanceSortImpl) value;
    request.setSort(
        SearchRequestSortMapper.toElementInstanceSearchQuerySortRequest(
            sorting.getSearchRequestProperty()));
    return this;
  }

  @Override
  public ElementInstanceSearchRequest sort(final Consumer<ElementInstanceSort> fn) {
    return sort(elementInstanceSort(fn));
  }

  @Override
  public ElementInstanceSearchRequest page(final SearchRequestPage value) {
    final SearchRequestPageImpl page = (SearchRequestPageImpl) value;
    request.setPage(page.getSearchRequestProperty());
    return this;
  }

  @Override
  public ElementInstanceSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected ElementInstanceSearchQuery getSearchRequestProperty() {
    return request;
  }
}
