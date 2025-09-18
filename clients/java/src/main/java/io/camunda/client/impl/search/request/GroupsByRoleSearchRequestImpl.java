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

import static io.camunda.client.api.search.request.SearchRequestBuilders.roleGroupSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.RoleGroupFilter;
import io.camunda.client.api.search.request.GroupsByRoleSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.RoleGroup;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.RoleGroupSort;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.RoleGroupSearchQueryRequest;
import io.camunda.client.protocol.rest.RoleGroupSearchResult;
import java.util.function.Consumer;

public class GroupsByRoleSearchRequestImpl
    extends AbstractSearchRequestImpl<RoleGroupSearchQueryRequest, RoleGroup>
    implements GroupsByRoleSearchRequest {

  private final RoleGroupSearchQueryRequest request;
  private final String roleId;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;

  public GroupsByRoleSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String roleId) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.roleId = roleId;
    request = new RoleGroupSearchQueryRequest();
  }

  @Override
  public CamundaFuture<SearchResponse<RoleGroup>> send() {
    ArgumentUtil.ensureNotNullNorEmpty("roleId", roleId);

    return httpClient.post(
        String.format("/roles/%s/groups/search", roleId),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        RoleGroupSearchResult.class,
        SearchResponseMapper::toRoleGroupsResponse,
        consistencyPolicy);
  }

  @Override
  public GroupsByRoleSearchRequest filter(final RoleGroupFilter value) {
    // this command does not support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public GroupsByRoleSearchRequest filter(final Consumer<RoleGroupFilter> fn) {
    // this command does not support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public GroupsByRoleSearchRequest sort(final RoleGroupSort value) {
    request.setSort(
        SearchRequestSortMapper.toRoleGroupSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public GroupsByRoleSearchRequest sort(final Consumer<RoleGroupSort> fn) {
    return sort(roleGroupSort(fn));
  }

  @Override
  public GroupsByRoleSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public GroupsByRoleSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected RoleGroupSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
