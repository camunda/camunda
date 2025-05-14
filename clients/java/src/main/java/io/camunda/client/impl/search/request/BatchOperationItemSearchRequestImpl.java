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

import static io.camunda.client.api.search.request.SearchRequestBuilders.batchOperationItemFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.batchOperationItemSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.BatchOperationItemFilter;
import io.camunda.client.api.search.request.BatchOperationItemSearchRequest;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.BatchOperationItemSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.BatchOperationItemSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class BatchOperationItemSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.BatchOperationItemSearchQuery>
    implements BatchOperationItemSearchRequest {

  private final io.camunda.client.protocol.rest.BatchOperationItemSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public BatchOperationItemSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new io.camunda.client.protocol.rest.BatchOperationItemSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public BatchOperationItemSearchRequest filter(final BatchOperationItemFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public BatchOperationItemSearchRequest filter(final Consumer<BatchOperationItemFilter> fn) {
    return filter(batchOperationItemFilter(fn));
  }

  @Override
  public BatchOperationItemSearchRequest sort(final BatchOperationItemSort value) {
    request.setSort(
        SearchRequestSortMapper.toBatchOperationItemSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public BatchOperationItemSearchRequest sort(final Consumer<BatchOperationItemSort> fn) {
    return sort(batchOperationItemSort(fn));
  }

  @Override
  public BatchOperationItemSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public BatchOperationItemSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected io.camunda.client.protocol.rest.BatchOperationItemSearchQuery
      getSearchRequestProperty() {
    return request;
  }

  @Override
  public FinalSearchRequestStep<BatchOperationItem> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<BatchOperationItem>> send() {
    final HttpCamundaFuture<SearchResponse<BatchOperationItem>> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/batch-operation-items/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        BatchOperationItemSearchQueryResult.class,
        SearchResponseMapper::toBatchOperationItemsResponse,
        result);
    return result;
  }
}
