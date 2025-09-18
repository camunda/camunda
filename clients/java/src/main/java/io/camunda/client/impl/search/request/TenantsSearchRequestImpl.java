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
import static io.camunda.client.api.search.request.SearchRequestBuilders.tenantFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.tenantSort;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.TenantFilter;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.request.TenantsSearchRequest;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.Tenant;
import io.camunda.client.api.search.sort.TenantSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.TenantSearchQueryRequest;
import io.camunda.client.protocol.rest.TenantSearchQueryResult;
import java.util.function.Consumer;

public class TenantsSearchRequestImpl
    extends AbstractSearchRequestImpl<TenantSearchQueryRequest, Tenant>
    implements TenantsSearchRequest {

  private final TenantSearchQueryRequest request;
  private final HttpClient httpClient;

  private final JsonMapper jsonMapper;

  public TenantsSearchRequestImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    request = new TenantSearchQueryRequest();
  }

  @Override
  public CamundaFuture<SearchResponse<Tenant>> send() {
    return httpClient.post(
        "/tenants/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        TenantSearchQueryResult.class,
        SearchResponseMapper::toTenantsResponse,
        consistencyPolicy);
  }

  @Override
  public TenantsSearchRequest filter(final TenantFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public TenantsSearchRequest filter(final Consumer<TenantFilter> fn) {
    return filter(tenantFilter(fn));
  }

  @Override
  public TenantsSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public TenantsSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  public TenantsSearchRequest sort(final TenantSort value) {
    request.setSort(
        SearchRequestSortMapper.toTenantSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public TenantsSearchRequest sort(final Consumer<TenantSort> fn) {
    return sort(tenantSort(fn));
  }

  @Override
  protected TenantSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
