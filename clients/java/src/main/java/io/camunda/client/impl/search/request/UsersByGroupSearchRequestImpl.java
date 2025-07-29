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

import static io.camunda.client.api.search.request.SearchRequestBuilders.groupUserSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.GroupUserFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.request.UsersByGroupSearchRequest;
import io.camunda.client.api.search.response.GroupUser;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.GroupUserSort;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.GroupUserSearchQueryRequest;
import io.camunda.client.protocol.rest.GroupUserSearchResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class UsersByGroupSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<GroupUserSearchQueryRequest>
    implements UsersByGroupSearchRequest {

  private final GroupUserSearchQueryRequest request;
  private final String groupId;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public UsersByGroupSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String groupId) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.groupId = groupId;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new GroupUserSearchQueryRequest();
  }

  @Override
  public FinalSearchRequestStep<GroupUser> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<GroupUser>> send() {
    ArgumentUtil.ensureNotNullNorEmpty("groupId", groupId);
    final HttpCamundaFuture<SearchResponse<GroupUser>> result = new HttpCamundaFuture<>();
    httpClient.post(
        String.format("/groups/%s/users/search", groupId),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        GroupUserSearchResult.class,
        SearchResponseMapper::toGroupUsersResponse,
        result);
    return result;
  }

  @Override
  public UsersByGroupSearchRequest filter(final GroupUserFilter value) {
    // This command doesn't support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public UsersByGroupSearchRequest filter(final Consumer<GroupUserFilter> fn) {
    // This command doesn't support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public UsersByGroupSearchRequest sort(final GroupUserSort value) {
    request.setSort(
        SearchRequestSortMapper.toGroupUserSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public UsersByGroupSearchRequest sort(final Consumer<GroupUserSort> fn) {
    return sort(groupUserSort((fn)));
  }

  @Override
  public UsersByGroupSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public UsersByGroupSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected GroupUserSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
