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

import static io.camunda.client.api.search.request.SearchRequestBuilders.processDefinitionStatisticsFilter;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.statistics.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.client.api.statistics.request.ProcessDefinitionElementStatisticsRequest;
import io.camunda.client.api.statistics.response.ProcessElementStatistics;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.ProcessDefinitionElementStatisticsQuery;
import io.camunda.client.protocol.rest.ProcessDefinitionElementStatisticsQueryResult;
import java.util.List;
import java.util.function.Consumer;

public class ProcessDefinitionElementStatisticsRequestImpl
    extends AbstractStatisticsRequestImpl<
        ProcessDefinitionElementStatisticsQuery, List<ProcessElementStatistics>>
    implements ProcessDefinitionElementStatisticsRequest {

  private final long processDefinitionKey;
  private final ProcessDefinitionElementStatisticsQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;

  public ProcessDefinitionElementStatisticsRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long processDefinitionKey) {
    super(httpClient.newRequestConfig());
    request = new ProcessDefinitionElementStatisticsQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    this.processDefinitionKey = processDefinitionKey;
  }

  @Override
  public CamundaFuture<List<ProcessElementStatistics>> send() {
    return httpClient.post(
        "/process-definitions/" + processDefinitionKey + "/statistics/element-instances",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ProcessDefinitionElementStatisticsQueryResult.class,
        StatisticsResponseMapper::toProcessDefinitionStatisticsResponse,
        consistencyPolicy);
  }

  @Override
  public ProcessDefinitionElementStatisticsRequest filter(
      final ProcessDefinitionStatisticsFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ProcessDefinitionElementStatisticsRequest filter(
      final Consumer<ProcessDefinitionStatisticsFilter> fn) {
    return filter(processDefinitionStatisticsFilter(fn));
  }

  @Override
  protected ProcessDefinitionElementStatisticsQuery getSearchRequestProperty() {
    return request;
  }
}
