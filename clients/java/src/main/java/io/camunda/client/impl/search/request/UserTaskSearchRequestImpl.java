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
import static io.camunda.client.api.search.request.SearchRequestBuilders.userTaskFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.userTaskSort;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.request.UserTaskSearchRequest;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.sort.UserTaskSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.UserTaskSearchQuery;
import io.camunda.client.protocol.rest.UserTaskSearchQueryResult;
import java.util.function.Consumer;

public class UserTaskSearchRequestImpl
    extends AbstractSearchRequestImpl<UserTaskSearchQuery, UserTask>
    implements UserTaskSearchRequest {

  private final UserTaskSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;

  public UserTaskSearchRequestImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    request = new UserTaskSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
  }

  @Override
  public HttpCamundaFuture<SearchResponse<UserTask>> send() {
    return httpClient.post(
        "/user-tasks/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        UserTaskSearchQueryResult.class,
        SearchResponseMapper::toUserTaskSearchResponse,
        consistencyPolicy);
  }

  @Override
  public UserTaskSearchRequest filter(final UserTaskFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public UserTaskSearchRequest filter(final Consumer<UserTaskFilter> fn) {
    return filter(userTaskFilter(fn));
  }

  @Override
  public UserTaskSearchRequest sort(final UserTaskSort value) {
    request.setSort(
        SearchRequestSortMapper.toUserTaskSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public UserTaskSearchRequest sort(final Consumer<UserTaskSort> fn) {
    return sort(userTaskSort(fn));
  }

  @Override
  public UserTaskSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public UserTaskSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected UserTaskSearchQuery getSearchRequestProperty() {
    return request;
  }
}
