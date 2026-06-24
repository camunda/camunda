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
import static io.camunda.client.api.search.request.SearchRequestBuilders.resourceFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.resourceSort;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.Resource;
import io.camunda.client.api.search.filter.ResourceFilter;
import io.camunda.client.api.search.page.AnyPage;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.ResourceSearchRequest;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.ResourceSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.ResourceSearchQuery;
import io.camunda.client.protocol.rest.ResourceSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public final class ResourceSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<ResourceSearchQuery>
    implements ResourceSearchRequest {

  private final ResourceSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ResourceSearchRequestImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new ResourceSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<Resource> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public HttpCamundaFuture<SearchResponse<Resource>> send() {
    final HttpCamundaFuture<SearchResponse<Resource>> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/resources/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ResourceSearchQueryResult.class,
        SearchResponseMapper::toResourceSearchResponse,
        result);
    return result;
  }

  @Override
  public ResourceSearchRequest filter(final ResourceFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ResourceSearchRequest filter(final Consumer<ResourceFilter> fn) {
    return filter(resourceFilter(fn));
  }

  @Override
  public ResourceSearchRequest sort(final ResourceSort value) {
    request.setSort(
        SearchRequestSortMapper.toResourceSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public ResourceSearchRequest sort(final Consumer<ResourceSort> fn) {
    return sort(resourceSort(fn));
  }

  @Override
  public ResourceSearchRequest page(final AnyPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ResourceSearchRequest page(final Consumer<AnyPage> fn) {
    return page(anyPage(fn));
  }

  @Override
  protected ResourceSearchQuery getSearchRequestProperty() {
    return request;
  }
}
