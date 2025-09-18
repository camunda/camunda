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
import static io.camunda.client.api.search.request.SearchRequestBuilders.userTaskVariableFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.variableSort;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.UserTaskVariableFilter;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.request.UserTaskVariableSearchRequest;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.api.search.sort.VariableSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.UserTaskVariableSearchQueryRequest;
import io.camunda.client.protocol.rest.VariableSearchQueryResult;
import java.util.function.Consumer;

public class UserTaskVariableSearchRequestImpl
    extends AbstractSearchRequestImpl<UserTaskVariableSearchQueryRequest, Variable>
    implements UserTaskVariableSearchRequest {

  private final UserTaskVariableSearchQueryRequest request;
  private final HttpClient httpClient;

  private final JsonMapper jsonMapper;
  private final long userTaskKey;

  public UserTaskVariableSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long userTaskKey) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.userTaskKey = userTaskKey;
    request = new UserTaskVariableSearchQueryRequest();
  }

  @Override
  public CamundaFuture<SearchResponse<Variable>> send() {
    return httpClient.post(
        String.format("/user-tasks/%d/variables/search", userTaskKey),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        VariableSearchQueryResult.class,
        SearchResponseMapper::toVariableSearchResponse,
        consistencyPolicy);
  }

  @Override
  public UserTaskVariableSearchRequest filter(final UserTaskVariableFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public UserTaskVariableSearchRequest filter(final Consumer<UserTaskVariableFilter> fn) {
    return filter(userTaskVariableFilter(fn));
  }

  @Override
  public UserTaskVariableSearchRequest sort(final VariableSort value) {
    request.setSort(
        SearchRequestSortMapper.toUserTaskVariableSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public UserTaskVariableSearchRequest sort(final Consumer<VariableSort> fn) {
    return sort(variableSort(fn));
  }

  @Override
  public UserTaskVariableSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
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
