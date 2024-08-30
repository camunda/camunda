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
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.userTaskFilter;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.userTaskSort;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.search.SearchRequestPage;
import io.camunda.zeebe.client.api.search.filter.UserTaskFilter;
import io.camunda.zeebe.client.api.search.query.FinalSearchQueryStep;
import io.camunda.zeebe.client.api.search.query.UserTaskQuery;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.client.api.search.response.UserTask;
import io.camunda.zeebe.client.api.search.sort.UserTaskSort;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.search.SearchRequestPageImpl;
import io.camunda.zeebe.client.impl.search.SearchResponseMapper;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.impl.search.sort.UserTaskSortImpl;
import io.camunda.zeebe.client.protocol.rest.UserTaskFilterRequest;
import io.camunda.zeebe.client.protocol.rest.UserTaskSearchQueryRequest;
import io.camunda.zeebe.client.protocol.rest.UserTaskSearchQueryResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class UserTaskQueryImpl
    extends TypedSearchRequestPropertyProvider<UserTaskSearchQueryRequest>
    implements UserTaskQuery {

  private final UserTaskSearchQueryRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public UserTaskQueryImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new UserTaskSearchQueryRequest();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchQueryStep<UserTask> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public HttpZeebeFuture<SearchQueryResponse<UserTask>> send() {
    final HttpZeebeFuture<SearchQueryResponse<UserTask>> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/user-tasks/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        UserTaskSearchQueryResponse.class,
        SearchResponseMapper::toUserTaskSearchResponse,
        result);
    return result;
  }

  @Override
  public UserTaskQuery filter(final UserTaskFilter value) {
    final UserTaskFilterRequest filter = provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public UserTaskQuery filter(final Consumer<UserTaskFilter> fn) {
    return filter(userTaskFilter(fn));
  }

  @Override
  public UserTaskQuery sort(final UserTaskSort value) {
    final UserTaskSortImpl sorting = (UserTaskSortImpl) value;
    request.setSort(sorting.getSearchRequestProperty());
    return this;
  }

  @Override
  public UserTaskQuery sort(final Consumer<UserTaskSort> fn) {
    return sort(userTaskSort(fn));
  }

  @Override
  public UserTaskQuery page(final SearchRequestPage value) {
    final SearchRequestPageImpl page = (SearchRequestPageImpl) value;
    request.setPage(page.getSearchRequestProperty());
    return this;
  }

  @Override
  public UserTaskQuery page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected UserTaskSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
