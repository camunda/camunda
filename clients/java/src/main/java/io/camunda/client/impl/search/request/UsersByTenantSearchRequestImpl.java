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
import static io.camunda.client.api.search.request.SearchRequestBuilders.tenantUserSort;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.TenantUserFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.request.UsersByTenantSearchRequest;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.TenantUser;
import io.camunda.client.api.search.sort.TenantUserSort;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.TenantUserSearchQueryRequest;
import io.camunda.client.protocol.rest.TenantUserSearchResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class UsersByTenantSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<TenantUserSearchQueryRequest>
    implements UsersByTenantSearchRequest {

  private final TenantUserSearchQueryRequest request;
  private final String tenantId;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public UsersByTenantSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String tenantId) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.tenantId = tenantId;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new TenantUserSearchQueryRequest();
  }

  @Override
  public FinalSearchRequestStep<TenantUser> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<TenantUser>> send() {
    ArgumentUtil.ensureNotNullNorEmpty("tenantId", tenantId);
    final HttpCamundaFuture<SearchResponse<TenantUser>> result = new HttpCamundaFuture<>();
    httpClient.post(
        String.format("/tenants/%s/users/search", tenantId),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        TenantUserSearchResult.class,
        SearchResponseMapper::toTenantUsersResponse,
        result);
    return result;
  }

  @Override
  public UsersByTenantSearchRequest filter(final TenantUserFilter value) {
    // This command doesn't support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public UsersByTenantSearchRequest filter(final Consumer<TenantUserFilter> fn) {
    // This command doesn't support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public UsersByTenantSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public UsersByTenantSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  public UsersByTenantSearchRequest sort(final TenantUserSort value) {
    request.setSort(
        SearchRequestSortMapper.toTenantUserSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public UsersByTenantSearchRequest sort(final Consumer<TenantUserSort> fn) {
    return sort(tenantUserSort(fn));
  }

  @Override
  protected TenantUserSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
