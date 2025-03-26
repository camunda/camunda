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
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.request.VariableSearchRequest;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.api.search.sort.VariableSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.impl.search.sort.VariableSortImpl;
import io.camunda.client.protocol.rest.VariableSearchQuery;
import io.camunda.client.protocol.rest.VariableSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class VariableSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<VariableSearchQuery>
    implements VariableSearchRequest {

  private final VariableSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public VariableSearchRequestImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new VariableSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<Variable> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<Variable>> send() {
    final HttpCamundaFuture<SearchResponse<Variable>> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/variables/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        VariableSearchQueryResult.class,
        SearchResponseMapper::toVariableSearchResponse,
        result);
    return result;
  }

  @Override
  public VariableSearchRequest filter(final VariableFilter value) {
    final io.camunda.client.protocol.rest.VariableFilter filter =
        provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public VariableSearchRequest filter(final Consumer<VariableFilter> fn) {
    return filter(variableFilter(fn));
  }

  @Override
  public VariableSearchRequest sort(final VariableSort value) {
    final VariableSortImpl sorting = (VariableSortImpl) value;
    request.setSort(
        SearchRequestSortMapper.toVariableSearchQuerySortRequest(
            sorting.getSearchRequestProperty()));
    return this;
  }

  @Override
  public VariableSearchRequest sort(final Consumer<VariableSort> fn) {
    return sort(variableSort(fn));
  }

  @Override
  public VariableSearchRequest page(final SearchRequestPage value) {
    final SearchRequestPageImpl page = (SearchRequestPageImpl) value;
    request.setPage(page.getSearchRequestProperty());
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
