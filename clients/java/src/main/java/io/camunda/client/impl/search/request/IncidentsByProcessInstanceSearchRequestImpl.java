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

import static io.camunda.client.api.search.request.SearchRequestBuilders.incidentSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.processInstanceIncidentFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;
import static io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider.provideSearchRequestProperty;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.ProcessInstanceIncidentFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.IncidentsByProcessInstanceSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.IncidentSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.IncidentSearchQueryResult;
import io.camunda.client.protocol.rest.ProcessInstanceIncidentSearchQuery;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class IncidentsByProcessInstanceSearchRequestImpl
    implements IncidentsByProcessInstanceSearchRequest {

  private final ProcessInstanceIncidentSearchQuery request;
  private final long processInstanceKey;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public IncidentsByProcessInstanceSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long processInstanceKey) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.processInstanceKey = processInstanceKey;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new ProcessInstanceIncidentSearchQuery();
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
        String.format("/process-instances/%d/incidents/search", processInstanceKey),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        IncidentSearchQueryResult.class,
        SearchResponseMapper::toIncidentSearchResponse,
        result);
    return result;
  }

  @Override
  public IncidentsByProcessInstanceSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public IncidentsByProcessInstanceSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  public IncidentsByProcessInstanceSearchRequest filter(final ProcessInstanceIncidentFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public IncidentsByProcessInstanceSearchRequest filter(
      final Consumer<ProcessInstanceIncidentFilter> fn) {
    return filter(processInstanceIncidentFilter(fn));
  }

  @Override
  public IncidentsByProcessInstanceSearchRequest sort(final IncidentSort value) {
    request.setSort(
        SearchRequestSortMapper.toIncidentSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public IncidentsByProcessInstanceSearchRequest sort(final Consumer<IncidentSort> fn) {
    return sort(incidentSort(fn));
  }
}
