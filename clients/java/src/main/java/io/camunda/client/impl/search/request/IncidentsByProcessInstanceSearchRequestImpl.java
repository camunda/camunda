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
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;
import static io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider.provideSearchRequestProperty;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.request.IncidentsByProcessInstanceSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.IncidentSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.IncidentSearchQueryResult;
import io.camunda.client.protocol.rest.ProcessInstanceIncidentSearchQuery;
import java.util.function.Consumer;

public class IncidentsByProcessInstanceSearchRequestImpl
    extends AbstractSearchRequestImpl<ProcessInstanceIncidentSearchQuery, Incident>
    implements IncidentsByProcessInstanceSearchRequest {

  private final ProcessInstanceIncidentSearchQuery request;
  private final long processInstanceKey;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;

  public IncidentsByProcessInstanceSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long processInstanceKey) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.processInstanceKey = processInstanceKey;
    request = new ProcessInstanceIncidentSearchQuery();
  }

  @Override
  public CamundaFuture<SearchResponse<Incident>> send() {

    return httpClient.post(
        String.format("/process-instances/%d/incidents/search", processInstanceKey),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        IncidentSearchQueryResult.class,
        SearchResponseMapper::toIncidentSearchResponse,
        consistencyPolicy);
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
  protected ProcessInstanceIncidentSearchQuery getSearchRequestProperty() {
    return request;
  }
}
