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

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.statistics.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.client.api.statistics.request.ProcessDefinitionElementStatisticsRequest;
import io.camunda.client.api.statistics.response.ProcessElementStatistics;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.statistics.filter.ProcessDefinitionStatisticsFilterImpl;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.BaseProcessInstanceFilter;
import io.camunda.client.protocol.rest.ProcessDefinitionElementStatisticsQuery;
import io.camunda.client.protocol.rest.ProcessDefinitionElementStatisticsQueryResult;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ProcessDefinitionElementStatisticsRequestImpl
    extends TypedSearchRequestPropertyProvider<ProcessDefinitionElementStatisticsQuery>
    implements ProcessDefinitionElementStatisticsRequest {

  private final long processDefinitionKey;
  private final ProcessDefinitionElementStatisticsQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ProcessDefinitionElementStatisticsRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long processDefinitionKey) {
    request = new ProcessDefinitionElementStatisticsQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    this.processDefinitionKey = processDefinitionKey;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalCommandStep<List<ProcessElementStatistics>> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<List<ProcessElementStatistics>> send() {
    final HttpCamundaFuture<List<ProcessElementStatistics>> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/process-definitions/" + processDefinitionKey + "/statistics/element-instances",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ProcessDefinitionElementStatisticsQueryResult.class,
        StatisticsResponseMapper::toProcessDefinitionStatisticsResponse,
        result);
    return result;
  }

  @Override
  public ProcessDefinitionElementStatisticsRequest filter(
      final ProcessDefinitionStatisticsFilter value) {
    final BaseProcessInstanceFilter filter = provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public ProcessDefinitionElementStatisticsRequest filter(
      final Consumer<ProcessDefinitionStatisticsFilter> fn) {
    final ProcessDefinitionStatisticsFilter value = new ProcessDefinitionStatisticsFilterImpl();
    fn.accept(value);
    final BaseProcessInstanceFilter filter = provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  protected ProcessDefinitionElementStatisticsQuery getSearchRequestProperty() {
    return request;
  }
}
