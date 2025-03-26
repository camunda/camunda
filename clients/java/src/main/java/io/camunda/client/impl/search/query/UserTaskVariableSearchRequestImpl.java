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
package io.camunda.client.impl.search.query;

import static io.camunda.client.api.search.SearchRequestBuilders.searchRequestPage;
import static io.camunda.client.api.search.SearchRequestBuilders.userTaskVariableFilter;
import static io.camunda.client.api.search.SearchRequestBuilders.variableSort;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.SearchRequestPage;
import io.camunda.client.api.search.filter.UserTaskVariableFilter;
import io.camunda.client.api.search.query.FinalSearchQueryStep;
import io.camunda.client.api.search.query.UserTaskVariableSearchRequest;
import io.camunda.client.api.search.response.SearchQueryResponse;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.api.search.sort.VariableSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.SearchQuerySortRequestMapper;
import io.camunda.client.impl.search.SearchRequestPageImpl;
import io.camunda.client.impl.search.SearchResponseMapper;
import io.camunda.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.search.sort.VariableSortImpl;
import io.camunda.client.protocol.rest.UserTaskVariableSearchQueryRequest;
import io.camunda.client.protocol.rest.VariableSearchQueryResult;
import io.camunda.client.protocol.rest.VariableUserTaskFilterRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class UserTaskVariableSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<UserTaskVariableSearchQueryRequest>
    implements UserTaskVariableSearchRequest {

  private final UserTaskVariableSearchQueryRequest request;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final JsonMapper jsonMapper;
  private final long userTaskKey;

  public UserTaskVariableSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long userTaskKey) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.userTaskKey = userTaskKey;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new UserTaskVariableSearchQueryRequest();
  }

  @Override
  public FinalSearchQueryStep<Variable> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchQueryResponse<Variable>> send() {
    final HttpCamundaFuture<SearchQueryResponse<Variable>> result = new HttpCamundaFuture<>();
    httpClient.post(
        String.format("/user-tasks/%d/variables/search", userTaskKey),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        VariableSearchQueryResult.class,
        SearchResponseMapper::toVariableSearchResponse,
        result);
    return result;
  }

  @Override
  public UserTaskVariableSearchRequest filter(final UserTaskVariableFilter value) {
    final VariableUserTaskFilterRequest filter = provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public UserTaskVariableSearchRequest filter(final Consumer<UserTaskVariableFilter> fn) {
    return filter(userTaskVariableFilter(fn));
  }

  @Override
  public UserTaskVariableSearchRequest sort(final VariableSort value) {
    final VariableSortImpl sorting = (VariableSortImpl) value;
    request.setSort(
        SearchQuerySortRequestMapper.toUserTaskVariableSearchQuerySortRequest(
            sorting.getSearchRequestProperty()));
    return this;
  }

  @Override
  public UserTaskVariableSearchRequest sort(final Consumer<VariableSort> fn) {
    return sort(variableSort(fn));
  }

  @Override
  public UserTaskVariableSearchRequest page(final SearchRequestPage value) {
    final SearchRequestPageImpl page = (SearchRequestPageImpl) value;
    request.setPage(page.getSearchRequestProperty());
    return this;
  }

  @Override
  public UserTaskVariableSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected UserTaskVariableSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
