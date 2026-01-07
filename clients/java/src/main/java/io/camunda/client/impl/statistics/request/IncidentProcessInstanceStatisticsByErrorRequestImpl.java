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
package io.camunda.client.impl.statistics.request;

import static io.camunda.client.api.search.request.SearchRequestBuilders.offsetPage;
import static io.camunda.client.api.statistics.request.StatisticsRequestBuilders.incidentProcessInstanceStatisticsByErrorSort;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestOffsetPage;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.statistics.request.IncidentProcessInstanceStatisticsByErrorRequest;
import io.camunda.client.api.statistics.response.IncidentProcessInstanceStatisticsByError;
import io.camunda.client.api.statistics.sort.IncidentProcessInstanceStatisticsByErrorSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByErrorQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public final class IncidentProcessInstanceStatisticsByErrorRequestImpl
    extends TypedSearchRequestPropertyProvider<IncidentProcessInstanceStatisticsByErrorQuery>
    implements IncidentProcessInstanceStatisticsByErrorRequest {

  private final IncidentProcessInstanceStatisticsByErrorQuery request;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public IncidentProcessInstanceStatisticsByErrorRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new IncidentProcessInstanceStatisticsByErrorQuery();
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<IncidentProcessInstanceStatisticsByError> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<IncidentProcessInstanceStatisticsByError>> send() {
    final HttpCamundaFuture<SearchResponse<IncidentProcessInstanceStatisticsByError>> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/incidents/statistics/process-instances-by-error",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        IncidentProcessInstanceStatisticsByErrorQueryResult.class,
        StatisticsResponseMapper::toIncidentProcessInstanceStatisticsByErrorResponse,
        result);
    return result;
  }

  @Override
  public IncidentProcessInstanceStatisticsByErrorRequest page(final SearchRequestOffsetPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public IncidentProcessInstanceStatisticsByErrorRequest page(
      final Consumer<SearchRequestOffsetPage> fn) {
    return page(offsetPage(fn));
  }

  @Override
  public IncidentProcessInstanceStatisticsByErrorRequest sort(
      final IncidentProcessInstanceStatisticsByErrorSort value) {
    request.setSort(
        StatisticsRequestSortMapper.toIncidentProcessInstanceStatisticsByErrorSortRequests(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public IncidentProcessInstanceStatisticsByErrorRequest sort(
      final Consumer<IncidentProcessInstanceStatisticsByErrorSort> fn) {
    return sort(incidentProcessInstanceStatisticsByErrorSort(fn));
  }

  @Override
  protected IncidentProcessInstanceStatisticsByErrorQuery getSearchRequestProperty() {
    return request;
  }
}
