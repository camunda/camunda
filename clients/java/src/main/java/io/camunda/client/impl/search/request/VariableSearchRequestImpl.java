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

import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;
import static io.camunda.client.api.search.request.SearchRequestBuilders.variableFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.variableSort;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.request.VariableSearchRequest;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.api.search.sort.VariableSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.VariableSearchQuery;
import io.camunda.client.protocol.rest.VariableSearchQueryResult;
import java.util.function.Consumer;

public class VariableSearchRequestImpl
    extends AbstractSearchRequestImpl<VariableSearchQuery, Variable>
    implements VariableSearchRequest {

  private final VariableSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;

  public VariableSearchRequestImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    request = new VariableSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
  }

  @Override
  public CamundaFuture<SearchResponse<Variable>> send() {
    return httpClient.post(
        "/variables/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        VariableSearchQueryResult.class,
        SearchResponseMapper::toVariableSearchResponse,
        consistencyPolicy);
  }

  @Override
  public VariableSearchRequest filter(final VariableFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public VariableSearchRequest filter(final Consumer<VariableFilter> fn) {
    return filter(variableFilter(fn));
  }

  @Override
  public VariableSearchRequest sort(final VariableSort value) {
    request.setSort(
        SearchRequestSortMapper.toVariableSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public VariableSearchRequest sort(final Consumer<VariableSort> fn) {
    return sort(variableSort(fn));
  }

  @Override
  public VariableSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public VariableSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected VariableSearchQuery getSearchRequestProperty() {
    return request;
  }
}
