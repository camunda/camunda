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

import static io.camunda.client.api.search.request.SearchRequestBuilders.authorizationFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.authorizationSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.AuthorizationFilter;
import io.camunda.client.api.search.request.AuthorizationsSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.AuthorizationSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.AuthorizationSearchQuery;
import io.camunda.client.protocol.rest.AuthorizationSearchResult;
import java.util.function.Consumer;

public class AuthorizationsSearchRequestImpl
    extends AbstractSearchRequestImpl<AuthorizationSearchQuery, Authorization>
    implements AuthorizationsSearchRequest {

  private final AuthorizationSearchQuery request;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;

  public AuthorizationsSearchRequestImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    request = new AuthorizationSearchQuery();
  }

  @Override
  public CamundaFuture<SearchResponse<Authorization>> send() {
    return httpClient.post(
        "/authorizations/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        AuthorizationSearchResult.class,
        SearchResponseMapper::toAuthorizationsResponse,
        consistencyPolicy);
  }

  @Override
  public AuthorizationsSearchRequest filter(final AuthorizationFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuthorizationsSearchRequest filter(final Consumer<AuthorizationFilter> fn) {
    return filter(authorizationFilter(fn));
  }

  @Override
  public AuthorizationsSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public AuthorizationsSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  public AuthorizationsSearchRequest sort(final AuthorizationSort value) {
    request.setSort(
        SearchRequestSortMapper.toAuthorizationSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public AuthorizationsSearchRequest sort(final Consumer<AuthorizationSort> fn) {
    return sort(authorizationSort(fn));
  }

  @Override
  protected AuthorizationSearchQuery getSearchRequestProperty() {
    return request;
  }
}
