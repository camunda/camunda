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
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.ElementInstanceSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.ElementInstanceSearchQuery;
import io.camunda.client.protocol.rest.ElementInstanceSearchQueryResult;
import java.util.function.Consumer;

public class ElementInstanceSearchRequestImpl
    extends AbstractSearchRequestImpl<ElementInstanceSearchQuery, ElementInstance>
    implements ElementInstanceSearchRequest {

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final ElementInstanceSearchQuery request;

  public ElementInstanceSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    request = new ElementInstanceSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
  }

  @Override
  public CamundaFuture<SearchResponse<ElementInstance>> send() {
    return httpClient.post(
        "/element-instances/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ElementInstanceSearchQueryResult.class,
        SearchResponseMapper::toElementInstanceSearchResponse,
        consistencyPolicy);
  }

  @Override
  public ElementInstanceSearchRequest filter(final ElementInstanceFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ElementInstanceSearchRequest filter(final Consumer<ElementInstanceFilter> fn) {
    return filter(elementInstanceFilter(fn));
  }

  @Override
  public ElementInstanceSearchRequest sort(final ElementInstanceSort value) {
    request.setSort(
        SearchRequestSortMapper.toElementInstanceSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public ElementInstanceSearchRequest sort(final Consumer<ElementInstanceSort> fn) {
    return sort(elementInstanceSort(fn));
  }

  @Override
  public ElementInstanceSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
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
