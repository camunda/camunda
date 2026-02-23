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
import static io.camunda.client.api.statistics.request.StatisticsRequestBuilders.processDefinitionInstanceVersionStatisticsFilter;
import static io.camunda.client.api.statistics.request.StatisticsRequestBuilders.processDefinitionInstanceVersionStatisticsSort;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestOffsetPage;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.statistics.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.client.api.statistics.request.ProcessDefinitionInstanceVersionStatisticsRequest;
import io.camunda.client.api.statistics.response.ProcessDefinitionInstanceVersionStatistics;
import io.camunda.client.api.statistics.sort.ProcessDefinitionInstanceVersionStatisticsSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ProcessDefinitionInstanceVersionStatisticsRequestImpl
    extends TypedSearchRequestPropertyProvider<ProcessDefinitionInstanceVersionStatisticsQuery>
    implements ProcessDefinitionInstanceVersionStatisticsRequest {

  private final ProcessDefinitionInstanceVersionStatisticsQuery request;
  private final String processDefinitionId;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ProcessDefinitionInstanceVersionStatisticsRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String processDefinitionId) {
    request = new ProcessDefinitionInstanceVersionStatisticsQuery();
    this.jsonMapper = jsonMapper;
    this.processDefinitionId = processDefinitionId;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();

    request.setFilter(
        provideSearchRequestProperty(
            processDefinitionInstanceVersionStatisticsFilter(
                f -> f.processDefinitionId(processDefinitionId))));
  }

  @Override
  public FinalSearchRequestStep<ProcessDefinitionInstanceVersionStatistics> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<ProcessDefinitionInstanceVersionStatistics>> send() {
    final HttpCamundaFuture<SearchResponse<ProcessDefinitionInstanceVersionStatistics>> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/process-definitions/statistics/process-instances-by-version",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ProcessDefinitionInstanceVersionStatisticsQueryResult.class,
        StatisticsResponseMapper::toProcessDefinitionInstanceVersionStatisticsResponse,
        result);
    return result;
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsRequest page(
      final SearchRequestOffsetPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsRequest page(
      final Consumer<SearchRequestOffsetPage> fn) {
    return page(offsetPage(fn));
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsRequest filter(
      final ProcessDefinitionInstanceVersionStatisticsFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    ensureProcessDefinitionIdInFilter();
    return this;
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsRequest filter(
      final Consumer<ProcessDefinitionInstanceVersionStatisticsFilter> fn) {
    return filter(processDefinitionInstanceVersionStatisticsFilter(fn));
  }

  private void ensureProcessDefinitionIdInFilter() {
    request.getFilter().setProcessDefinitionId(processDefinitionId);
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsRequest sort(
      final ProcessDefinitionInstanceVersionStatisticsSort value) {
    request.setSort(
        StatisticsRequestSortMapper.toProcessDefinitionInstanceVersionStatisticsSortRequests(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsRequest sort(
      final Consumer<ProcessDefinitionInstanceVersionStatisticsSort> fn) {
    return sort(processDefinitionInstanceVersionStatisticsSort(fn));
  }

  @Override
  protected ProcessDefinitionInstanceVersionStatisticsQuery getSearchRequestProperty() {
    return request;
  }
}
