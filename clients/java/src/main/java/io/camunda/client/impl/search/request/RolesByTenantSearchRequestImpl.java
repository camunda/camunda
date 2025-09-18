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

import static io.camunda.client.api.search.request.SearchRequestBuilders.roleFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.roleSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.RoleFilter;
import io.camunda.client.api.search.request.RolesByTenantSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.Role;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.RoleSort;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.RoleSearchQueryRequest;
import io.camunda.client.protocol.rest.RoleSearchQueryResult;
import java.util.function.Consumer;

public class RolesByTenantSearchRequestImpl
    extends AbstractSearchRequestImpl<RoleSearchQueryRequest, Role>
    implements RolesByTenantSearchRequest {

  private final RoleSearchQueryRequest request;
  private final String tenantId;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;

  public RolesByTenantSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String tenantId) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.tenantId = tenantId;
    request = new RoleSearchQueryRequest();
  }

  @Override
  public CamundaFuture<SearchResponse<Role>> send() {
    ArgumentUtil.ensureNotNullNorEmpty("tenantId", tenantId);

    return httpClient.post(
        String.format("/tenants/%s/roles/search", tenantId),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        RoleSearchQueryResult.class,
        SearchResponseMapper::toRolesResponse,
        consistencyPolicy);
  }

  @Override
  public RolesByTenantSearchRequest filter(final RoleFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public RolesByTenantSearchRequest filter(final Consumer<RoleFilter> fn) {
    return filter(roleFilter(fn));
  }

  @Override
  public RolesByTenantSearchRequest sort(final RoleSort value) {
    request.setSort(
        SearchRequestSortMapper.toRoleSearchQuerySortRequest(provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public RolesByTenantSearchRequest sort(final Consumer<RoleSort> fn) {
    return sort(roleSort(fn));
  }

  @Override
  public RolesByTenantSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public RolesByTenantSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected RoleSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
