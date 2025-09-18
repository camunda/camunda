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
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.request.UsersSearchRequest;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.User;
import io.camunda.client.api.search.sort.UserSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.UserSearchQueryRequest;
import io.camunda.client.protocol.rest.UserSearchResult;
import java.util.function.Consumer;

public class UsersSearchRequestImpl extends AbstractSearchRequestImpl<UserSearchQueryRequest, User>
    implements UsersSearchRequest {

  private final UserSearchQueryRequest request;
  private final HttpClient httpClient;

  private final JsonMapper jsonMapper;

  public UsersSearchRequestImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    request = new UserSearchQueryRequest();
  }

  @Override
  public CamundaFuture<SearchResponse<User>> send() {

    return httpClient.post(
        "/users/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        UserSearchResult.class,
        SearchResponseMapper::toUsersResponse,
        consistencyPolicy);
  }

  @Override
  public UsersSearchRequest filter(final UserFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public UsersSearchRequest filter(final Consumer<UserFilter> fn) {
    return filter(userFilter(fn));
  }

  @Override
  public UsersSearchRequest sort(final UserSort value) {
    request.setSort(
        SearchRequestSortMapper.toUserSearchQuerySortRequest(provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public UsersSearchRequest sort(final Consumer<UserSort> fn) {
    return sort(userSort(fn));
  }

  @Override
  public UsersSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public UsersSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected UserSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
