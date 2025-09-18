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
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.BatchOperationItemSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.BatchOperationItemSearchQueryResult;
import java.util.function.Consumer;

public class BatchOperationItemSearchRequestImpl
    extends AbstractSearchRequestImpl<
        io.camunda.client.protocol.rest.BatchOperationItemSearchQuery, BatchOperationItem>
    implements BatchOperationItemSearchRequest {

  private final io.camunda.client.protocol.rest.BatchOperationItemSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;

  public BatchOperationItemSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    request = new io.camunda.client.protocol.rest.BatchOperationItemSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
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
  public CamundaFuture<SearchResponse<BatchOperationItem>> send() {

    return httpClient.post(
        "/batch-operation-items/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        BatchOperationItemSearchQueryResult.class,
        SearchResponseMapper::toBatchOperationItemsResponse,
        consistencyPolicy);
  }
}
