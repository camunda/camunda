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

import static io.camunda.client.api.search.request.SearchRequestBuilders.groupFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.groupSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.fetch.GroupsSearchRequest;
import io.camunda.client.api.search.filter.GroupFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.Group;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.GroupSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.GroupSearchQueryRequest;
import io.camunda.client.protocol.rest.GroupSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class GroupSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<GroupSearchQueryRequest>
    implements GroupsSearchRequest {

  private final GroupSearchQueryRequest request;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final JsonMapper jsonMapper;

  public GroupSearchRequestImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new GroupSearchQueryRequest();
  }

  @Override
  public FinalSearchRequestStep<Group> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<Group>> send() {
    final HttpCamundaFuture<SearchResponse<Group>> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/groups/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        GroupSearchQueryResult.class,
        SearchResponseMapper::toGroupsResponse,
        result);
    return result;
  }

  @Override
  protected GroupSearchQueryRequest getSearchRequestProperty() {
    return request;
  }

  @Override
  public GroupsSearchRequest filter(final GroupFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public GroupsSearchRequest filter(final Consumer<GroupFilter> fn) {
    return filter(groupFilter(fn));
  }

  @Override
  public GroupsSearchRequest sort(final GroupSort value) {
    request.setSort(
        SearchRequestSortMapper.toGroupSearchQuerySortRequest(provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public GroupsSearchRequest sort(final Consumer<GroupSort> fn) {
    return sort(groupSort(fn));
  }

  @Override
  public GroupsSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public GroupsSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }
}
