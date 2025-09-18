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

import static io.camunda.client.api.search.request.SearchRequestBuilders.clientSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.ClientFilter;
import io.camunda.client.api.search.request.ClientsByRoleSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.Client;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.ClientSort;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.RoleClientSearchQueryRequest;
import io.camunda.client.protocol.rest.RoleClientSearchResult;
import java.util.function.Consumer;

public class ClientsByRoleSearchRequestImpl
    extends AbstractSearchRequestImpl<RoleClientSearchQueryRequest, Client>
    implements ClientsByRoleSearchRequest {

  private final RoleClientSearchQueryRequest request;
  private final String roleId;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;

  public ClientsByRoleSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String roleId) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.roleId = roleId;
    request = new RoleClientSearchQueryRequest();
  }

  @Override
  public CamundaFuture<SearchResponse<Client>> send() {
    ArgumentUtil.ensureNotNullNorEmpty("roleId", roleId);
    return httpClient.post(
        String.format("/roles/%s/clients/search", roleId),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        RoleClientSearchResult.class,
        SearchResponseMapper::toClientsResponse,
        consistencyPolicy);
  }

  @Override
  public ClientsByRoleSearchRequest filter(final ClientFilter value) {
    // This command doesn't support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public ClientsByRoleSearchRequest filter(final Consumer<ClientFilter> fn) {
    // This command doesn't support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public ClientsByRoleSearchRequest sort(final ClientSort value) {
    request.setSort(
        SearchRequestSortMapper.toRoleClientSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public ClientsByRoleSearchRequest sort(final Consumer<ClientSort> fn) {
    return sort(clientSort(fn));
  }

  @Override
  public ClientsByRoleSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ClientsByRoleSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected RoleClientSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
