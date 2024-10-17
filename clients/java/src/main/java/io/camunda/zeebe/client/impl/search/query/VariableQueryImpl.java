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

import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.searchRequestPage;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.variableFilter;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.variableSort;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.search.SearchRequestPage;
import io.camunda.zeebe.client.api.search.filter.VariableFilter;
import io.camunda.zeebe.client.api.search.query.FinalSearchQueryStep;
import io.camunda.zeebe.client.api.search.query.VariableQuery;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.client.api.search.response.Variable;
import io.camunda.zeebe.client.api.search.sort.VariableSort;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.search.SearchRequestPageImpl;
import io.camunda.zeebe.client.impl.search.SearchResponseMapper;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.impl.search.sort.VariableSortImpl;
import io.camunda.zeebe.client.protocol.rest.VariableFilterRequest;
import io.camunda.zeebe.client.protocol.rest.VariableSearchQueryRequest;
import io.camunda.zeebe.client.protocol.rest.VariableSearchQueryResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class VariableQueryImpl
    extends TypedSearchRequestPropertyProvider<VariableSearchQueryRequest>
    implements VariableQuery {

  private final VariableSearchQueryRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public VariableQueryImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new VariableSearchQueryRequest();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchQueryStep<Variable> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<SearchQueryResponse<Variable>> send() {
    final HttpZeebeFuture<SearchQueryResponse<Variable>> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/variables/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        VariableSearchQueryResponse.class,
        SearchResponseMapper::toVariableSearchResponse,
        result);
    return result;
  }

  @Override
  public VariableQuery filter(final VariableFilter value) {
    final VariableFilterRequest filter = provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public VariableQuery filter(final Consumer<VariableFilter> fn) {
    return filter(variableFilter(fn));
  }

  @Override
  public VariableQuery sort(final VariableSort value) {
    final VariableSortImpl sorting = (VariableSortImpl) value;
    request.setSort(sorting.getSearchRequestProperty());
    return this;
  }

  @Override
  public VariableQuery sort(final Consumer<VariableSort> fn) {
    return sort(variableSort(fn));
  }

  @Override
  public VariableQuery page(final SearchRequestPage value) {
    final SearchRequestPageImpl page = (SearchRequestPageImpl) value;
    request.setPage(page.getSearchRequestProperty());
    return this;
  }

  @Override
  public VariableQuery page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected VariableSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
