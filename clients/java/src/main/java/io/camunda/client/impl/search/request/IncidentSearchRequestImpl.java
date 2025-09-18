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

import static io.camunda.client.api.search.request.SearchRequestBuilders.incidentFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.incidentSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.request.IncidentSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.IncidentSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.IncidentSearchQuery;
import io.camunda.client.protocol.rest.IncidentSearchQueryResult;
import java.util.function.Consumer;

public class IncidentSearchRequestImpl
    extends AbstractSearchRequestImpl<IncidentSearchQuery, Incident>
    implements IncidentSearchRequest {

  private final IncidentSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;

  public IncidentSearchRequestImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    request = new IncidentSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
  }

  @Override
  public CamundaFuture<SearchResponse<Incident>> send() {

    return httpClient.post(
        "/incidents/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        IncidentSearchQueryResult.class,
        SearchResponseMapper::toIncidentSearchResponse,
        consistencyPolicy);
  }

  @Override
  public IncidentSearchRequest filter(final IncidentFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public IncidentSearchRequest filter(final Consumer<IncidentFilter> fn) {
    return filter(incidentFilter(fn));
  }

  @Override
  public IncidentSearchRequest sort(final IncidentSort value) {
    request.setSort(
        SearchRequestSortMapper.toIncidentSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public IncidentSearchRequest sort(final Consumer<IncidentSort> fn) {
    return sort(incidentSort(fn));
  }

  @Override
  public IncidentSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public IncidentSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected IncidentSearchQuery getSearchRequestProperty() {
    return request;
  }
}
