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
import static io.camunda.client.api.search.request.SearchRequestBuilders.tenantGroupSort;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.TenantGroupFilter;
import io.camunda.client.api.search.request.GroupsByTenantSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.TenantGroup;
import io.camunda.client.api.search.sort.TenantGroupSort;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.TenantGroupSearchQueryRequest;
import io.camunda.client.protocol.rest.TenantGroupSearchResult;
import java.util.function.Consumer;

public class GroupsByTenantSearchRequestImpl
    extends AbstractSearchRequestImpl<TenantGroupSearchQueryRequest, TenantGroup>
    implements GroupsByTenantSearchRequest {

  private final TenantGroupSearchQueryRequest request;
  private final String tenantId;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;

  public GroupsByTenantSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String tenantId) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.tenantId = tenantId;
    request = new TenantGroupSearchQueryRequest();
  }

  @Override
  public CamundaFuture<SearchResponse<TenantGroup>> send() {
    ArgumentUtil.ensureNotNullNorEmpty("tenantId", tenantId);

    return httpClient.post(
        String.format("/tenants/%s/groups/search", tenantId),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        TenantGroupSearchResult.class,
        SearchResponseMapper::toTenantGroupsResponse,
        consistencyPolicy);
  }

  @Override
  public GroupsByTenantSearchRequest filter(final TenantGroupFilter value) {
    // this command does not support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public GroupsByTenantSearchRequest filter(final Consumer<TenantGroupFilter> fn) {
    // this command does not support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public GroupsByTenantSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public GroupsByTenantSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  public GroupsByTenantSearchRequest sort(final TenantGroupSort value) {
    request.setSort(
        SearchRequestSortMapper.toTenantGroupSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public GroupsByTenantSearchRequest sort(final Consumer<TenantGroupSort> fn) {
    return sort(tenantGroupSort(fn));
  }

  @Override
  protected TenantGroupSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
