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
import static io.camunda.client.api.statistics.request.StatisticsRequestBuilders.incidentProcessInstanceStatisticsSort;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestOffsetPage;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.statistics.request.IncidentProcessInstanceStatisticsRequest;
import io.camunda.client.api.statistics.response.IncidentProcessInstanceStatistics;
import io.camunda.client.api.statistics.sort.IncidentProcessInstanceStatisticsSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsQuery;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public final class IncidentProcessInstanceStatisticsRequestImpl
    extends TypedSearchRequestPropertyProvider<IncidentProcessInstanceStatisticsQuery>
    implements IncidentProcessInstanceStatisticsRequest {

  private final IncidentProcessInstanceStatisticsQuery request;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public IncidentProcessInstanceStatisticsRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new IncidentProcessInstanceStatisticsQuery();
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<IncidentProcessInstanceStatistics> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<IncidentProcessInstanceStatistics>> send() {
    final HttpCamundaFuture<SearchResponse<IncidentProcessInstanceStatistics>> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/incidents/statistics/process-instances",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        IncidentProcessInstanceStatisticsQueryResult.class,
        StatisticsResponseMapper::toIncidentProcessInstanceStatisticsResponse,
        result);
    return result;
  }

  @Override
  public IncidentProcessInstanceStatisticsRequest page(final SearchRequestOffsetPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public IncidentProcessInstanceStatisticsRequest page(final Consumer<SearchRequestOffsetPage> fn) {
    return page(offsetPage(fn));
  }

  @Override
  public IncidentProcessInstanceStatisticsRequest sort(
      final IncidentProcessInstanceStatisticsSort value) {
    request.setSort(
        StatisticsRequestSortMapper.toIncidentProcessInstanceStatisticsSortRequests(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public IncidentProcessInstanceStatisticsRequest sort(
      final Consumer<IncidentProcessInstanceStatisticsSort> fn) {
    return sort(incidentProcessInstanceStatisticsSort(fn));
  }

  @Override
  protected IncidentProcessInstanceStatisticsQuery getSearchRequestProperty() {
    return request;
  }
}
