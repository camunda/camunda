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
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.statistics.request.ProcessInstanceFlowNodeStatisticsRequest;
import io.camunda.client.api.statistics.response.ProcessFlowNodeStatistics;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.ProcessDefinitionFlowNodeStatisticsQueryResult;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class ProcessInstanceFlowNodeStatisticsRequestImpl
    implements ProcessInstanceFlowNodeStatisticsRequest {

  private final long processInstanceKey;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ProcessInstanceFlowNodeStatisticsRequestImpl(
      final HttpClient httpClient, final long processInstanceKey) {
    this.httpClient = httpClient;
    this.processInstanceKey = processInstanceKey;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalCommandStep<List<ProcessFlowNodeStatistics>> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<List<ProcessFlowNodeStatistics>> send() {
    final HttpCamundaFuture<List<ProcessFlowNodeStatistics>> result = new HttpCamundaFuture<>();
    httpClient.get(
        "/process-instances/" + processInstanceKey + "/statistics/flownode-instances",
        httpRequestConfig.build(),
        ProcessDefinitionFlowNodeStatisticsQueryResult.class,
        StatisticsResponseMapper::toProcessDefinitionStatisticsResponse,
        result);
    return result;
  }
}
