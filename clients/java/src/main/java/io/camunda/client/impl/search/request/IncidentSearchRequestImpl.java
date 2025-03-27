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
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.IncidentSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.IncidentSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.IncidentSearchQuery;
import io.camunda.client.protocol.rest.IncidentSearchQueryResult;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class IncidentSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<IncidentSearchQuery>
    implements IncidentSearchRequest {

  private final IncidentSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public IncidentSearchRequestImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new IncidentSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<Incident> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<Incident>> send() {
    final HttpCamundaFuture<SearchResponse<Incident>> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/incidents/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        IncidentSearchQueryResult.class,
        SearchResponseMapper::toIncidentSearchResponse,
        result);
    return result;
  }

  @Override
  public IncidentSearchRequest filter(final IncidentFilter value) {
    final io.camunda.client.protocol.rest.IncidentFilter filter =
        provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public IncidentSearchRequest filter(final Consumer<IncidentFilter> fn) {
    return filter(incidentFilter(fn));
  }

  @Override
  public IncidentSearchRequest sort(final IncidentSort value) {
    final List<io.camunda.client.impl.search.request.SearchRequestSort> sorting =
        provideSearchRequestProperty(value);
    request.setSort(SearchRequestSortMapper.toIncidentSearchQuerySortRequest(sorting));
    return this;
  }

  @Override
  public IncidentSearchRequest sort(final Consumer<IncidentSort> fn) {
    return sort(incidentSort(fn));
  }

  @Override
  public IncidentSearchRequest page(final SearchRequestPage value) {
    final SearchRequestPageImpl page = (SearchRequestPageImpl) value;
    request.setPage(page.getSearchRequestProperty());
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
