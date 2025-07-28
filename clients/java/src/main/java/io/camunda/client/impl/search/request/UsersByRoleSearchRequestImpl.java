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

import static io.camunda.client.api.search.request.SearchRequestBuilders.roleUserSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.RoleUserFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.request.UsersByRoleSearchRequest;
import io.camunda.client.api.search.response.RoleUser;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.RoleUserSort;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.RoleUserSearchQueryRequest;
import io.camunda.client.protocol.rest.RoleUserSearchResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class UsersByRoleSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<RoleUserSearchQueryRequest>
    implements UsersByRoleSearchRequest {

  private final RoleUserSearchQueryRequest request;
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
    request = new RoleUserSearchQueryRequest();
  }

  @Override
  public FinalSearchRequestStep<RoleUser> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<RoleUser>> send() {
    ArgumentUtil.ensureNotNullNorEmpty("roleId", roleId);
    final HttpCamundaFuture<SearchResponse<RoleUser>> result = new HttpCamundaFuture<>();
    httpClient.post(
        String.format("/roles/%s/users/search", roleId),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        RoleUserSearchResult.class,
        SearchResponseMapper::toRoleUsersResponse,
        result);
    return result;
  }

  @Override
  public UsersByRoleSearchRequest filter(final RoleUserFilter value) {
    // This command doesn't support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public UsersByRoleSearchRequest filter(final Consumer<RoleUserFilter> fn) {
    // This command doesn't support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public UsersByRoleSearchRequest sort(final RoleUserSort value) {
    request.setSort(
        SearchRequestSortMapper.toRoleUserSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public UsersByRoleSearchRequest sort(final Consumer<RoleUserSort> fn) {
    return sort(roleUserSort(fn));
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
  protected RoleUserSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
