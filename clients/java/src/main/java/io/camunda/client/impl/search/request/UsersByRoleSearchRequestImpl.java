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
import static io.camunda.client.api.search.request.SearchRequestBuilders.userFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.userSort;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.UserFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.request.UsersByRoleSearchRequest;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.User;
import io.camunda.client.api.search.sort.UserSort;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.UserSearchQueryRequest;
import io.camunda.client.protocol.rest.UserSearchResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class UsersByRoleSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<UserSearchQueryRequest>
    implements UsersByRoleSearchRequest {

  private final UserSearchQueryRequest request;
  private final String roleId;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public UsersByRoleSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String roleId) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.roleId = roleId;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new UserSearchQueryRequest();
  }

  @Override
  public FinalSearchRequestStep<User> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<User>> send() {
    ArgumentUtil.ensureNotNullNorEmpty("roleId", roleId);
    final HttpCamundaFuture<SearchResponse<User>> result = new HttpCamundaFuture<>();
    httpClient.post(
        String.format("/roles/%s/users/search", roleId),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        UserSearchResult.class,
        SearchResponseMapper::toUsersResponse,
        result);
    return result;
  }

  @Override
  public UsersByRoleSearchRequest filter(final UserFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public UsersByRoleSearchRequest filter(final Consumer<UserFilter> fn) {
    return filter(userFilter(fn));
  }

  @Override
  public UsersByRoleSearchRequest sort(final UserSort value) {
    request.setSort(
        SearchRequestSortMapper.toUserSearchQuerySortRequest(provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public UsersByRoleSearchRequest sort(final Consumer<UserSort> fn) {
    return sort(userSort(fn));
  }

  @Override
  public UsersByRoleSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public UsersByRoleSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected UserSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
