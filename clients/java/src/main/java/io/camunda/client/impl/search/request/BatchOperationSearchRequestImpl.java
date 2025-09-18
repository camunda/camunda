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

import static io.camunda.client.api.search.request.SearchRequestBuilders.batchOperationFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.batchOperationSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.BatchOperationFilter;
import io.camunda.client.api.search.request.BatchOperationSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.BatchOperation;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.BatchOperationSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.BatchOperationSearchQueryResult;
import java.util.function.Consumer;

public class BatchOperationSearchRequestImpl
    extends AbstractSearchRequestImpl<
        io.camunda.client.protocol.rest.BatchOperationSearchQuery, BatchOperation>
    implements BatchOperationSearchRequest {

  private final io.camunda.client.protocol.rest.BatchOperationSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;

  public BatchOperationSearchRequestImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    request = new io.camunda.client.protocol.rest.BatchOperationSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
  }

  @Override
  public BatchOperationSearchRequest filter(final BatchOperationFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public BatchOperationSearchRequest filter(final Consumer<BatchOperationFilter> fn) {
    return filter(batchOperationFilter(fn));
  }

  @Override
  public BatchOperationSearchRequest sort(final BatchOperationSort value) {
    request.setSort(
        SearchRequestSortMapper.toBatchOperationSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public BatchOperationSearchRequest sort(final Consumer<BatchOperationSort> fn) {
    return sort(batchOperationSort(fn));
  }

  @Override
  public BatchOperationSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public BatchOperationSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected io.camunda.client.protocol.rest.BatchOperationSearchQuery getSearchRequestProperty() {
    return request;
  }

  @Override
  public CamundaFuture<SearchResponse<BatchOperation>> send() {
    return httpClient.post(
        "/batch-operations/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        BatchOperationSearchQueryResult.class,
        SearchResponseMapper::toBatchOperationsResponse,
        consistencyPolicy);
  }
}
