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
import static io.camunda.client.api.statistics.request.StatisticsRequestBuilders.processDefinitionInstanceStatisticsSort;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestOffsetPage;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.statistics.request.ProcessDefinitionInstanceStatisticsRequest;
import io.camunda.client.api.statistics.response.ProcessDefinitionInstanceStatistics;
import io.camunda.client.api.statistics.sort.ProcessDefinitionInstanceStatisticsSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceStatisticsQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ProcessDefinitionInstanceStatisticsRequestImpl
    extends TypedSearchRequestPropertyProvider<ProcessDefinitionInstanceStatisticsQuery>
    implements ProcessDefinitionInstanceStatisticsRequest {

  private final ProcessDefinitionInstanceStatisticsQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ProcessDefinitionInstanceStatisticsRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new ProcessDefinitionInstanceStatisticsQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<ProcessDefinitionInstanceStatistics> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<ProcessDefinitionInstanceStatistics>> send() {
    final HttpCamundaFuture<SearchResponse<ProcessDefinitionInstanceStatistics>> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/process-definitions/statistics/process-instances",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ProcessDefinitionInstanceStatisticsQueryResult.class,
        StatisticsResponseMapper::toProcessDefinitionInstanceStatisticsResponse,
        result);
    return result;
  }

  @Override
  public ProcessDefinitionInstanceStatisticsRequest page(final SearchRequestOffsetPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ProcessDefinitionInstanceStatisticsRequest page(
      final Consumer<SearchRequestOffsetPage> fn) {
    return page(offsetPage(fn));
  }

  @Override
  public ProcessDefinitionInstanceStatisticsRequest sort(
      final ProcessDefinitionInstanceStatisticsSort value) {
    request.setSort(
        StatisticsRequestSortMapper.toProcessDefinitionInstanceStatisticsSortRequests(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public ProcessDefinitionInstanceStatisticsRequest sort(
      final Consumer<ProcessDefinitionInstanceStatisticsSort> fn) {
    return sort(processDefinitionInstanceStatisticsSort(fn));
  }

  @Override
  protected ProcessDefinitionInstanceStatisticsQuery getSearchRequestProperty() {
    return request;
  }
}
