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
import static io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider.provideSearchRequestProperty;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.IncidentsByElementInstanceSearchRequest;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class IncidentsByElementInstanceSearchRequestImpl
    implements IncidentsByElementInstanceSearchRequest {

  private final IncidentSearchQuery request;
  private final long elementInstanceKey;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public IncidentsByElementInstanceSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long elementInstanceKey) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.elementInstanceKey = elementInstanceKey;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new IncidentSearchQuery();
  }

  @Override
  public FinalSearchRequestStep<Incident> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public IncidentsByElementInstanceSearchRequest sort(final IncidentSort value) {
    request.setSort(
        SearchRequestSortMapper.toIncidentSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public IncidentsByElementInstanceSearchRequest sort(final Consumer<IncidentSort> fn) {
    return sort(incidentSort(fn));
  }

  @Override
  public IncidentsByElementInstanceSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public IncidentsByElementInstanceSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  public IncidentsByElementInstanceSearchRequest filter(final IncidentFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public IncidentsByElementInstanceSearchRequest filter(final Consumer<IncidentFilter> fn) {
    return filter(incidentFilter(fn));
  }

  @Override
  public CamundaFuture<SearchResponse<Incident>> send() {
    final HttpCamundaFuture<SearchResponse<Incident>> result = new HttpCamundaFuture<>();
    httpClient.post(
        String.format("/element-instances/%d/incidents/search", elementInstanceKey),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        IncidentSearchQueryResult.class,
        SearchResponseMapper::toIncidentSearchResponse,
        result);
    return result;
  }
}
