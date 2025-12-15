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
import static io.camunda.client.api.statistics.request.StatisticsRequestBuilders.incidentStatisticsByErrorHashCodeSort;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestOffsetPage;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.statistics.request.IncidentStatisticsByErrorHashCodeRequest;
import io.camunda.client.api.statistics.response.IncidentStatisticsByErrorHashCode;
import io.camunda.client.api.statistics.sort.IncidentStatisticsByErrorHashCodeSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.IncidentStatisticsByErrorHashCodeQuery;
import io.camunda.client.protocol.rest.IncidentStatisticsByErrorHashCodeQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class IncidentStatisticsByErrorHashCodeRequestImpl
    extends TypedSearchRequestPropertyProvider<IncidentStatisticsByErrorHashCodeQuery>
    implements IncidentStatisticsByErrorHashCodeRequest {

  private final IncidentStatisticsByErrorHashCodeQuery request;
  private final String errorHashCode;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public IncidentStatisticsByErrorHashCodeRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String errorHashCode) {
    request = new IncidentStatisticsByErrorHashCodeQuery();
    this.jsonMapper = jsonMapper;
    this.errorHashCode = errorHashCode;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<IncidentStatisticsByErrorHashCode> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<IncidentStatisticsByErrorHashCode>> send() {
    final HttpCamundaFuture<SearchResponse<IncidentStatisticsByErrorHashCode>> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        String.format("/incidents/%s/statistics", errorHashCode),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        IncidentStatisticsByErrorHashCodeQueryResult.class,
        StatisticsResponseMapper::toIncidentStatisticsByErrorHashCodeResponse,
        result);
    return result;
  }

  @Override
  public IncidentStatisticsByErrorHashCodeRequest page(final SearchRequestOffsetPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public IncidentStatisticsByErrorHashCodeRequest page(final Consumer<SearchRequestOffsetPage> fn) {
    return page(offsetPage(fn));
  }

  @Override
  public IncidentStatisticsByErrorHashCodeRequest sort(
      final IncidentStatisticsByErrorHashCodeSort value) {
    request.setSort(
        StatisticsRequestSortMapper.toIncidentStatisticsByErrorHashCodeSortRequests(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public IncidentStatisticsByErrorHashCodeRequest sort(
      final Consumer<IncidentStatisticsByErrorHashCodeSort> fn) {
    return sort(incidentStatisticsByErrorHashCodeSort(fn));
  }

  @Override
  protected IncidentStatisticsByErrorHashCodeQuery getSearchRequestProperty() {
    return request;
  }
}
