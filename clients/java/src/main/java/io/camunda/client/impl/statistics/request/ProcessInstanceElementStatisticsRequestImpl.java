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
import io.camunda.client.api.statistics.request.ProcessInstanceElementStatisticsRequest;
import io.camunda.client.api.statistics.response.ProcessElementStatistics;
import io.camunda.client.impl.fetch.AbstractFetchRequestImpl;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.ProcessDefinitionElementStatisticsQueryResult;
import java.util.List;

public class ProcessInstanceElementStatisticsRequestImpl
    extends AbstractFetchRequestImpl<List<ProcessElementStatistics>>
    implements ProcessInstanceElementStatisticsRequest {

  private final long processInstanceKey;
  private final HttpClient httpClient;

  public ProcessInstanceElementStatisticsRequestImpl(
      final HttpClient httpClient, final long processInstanceKey) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public CamundaFuture<List<ProcessElementStatistics>> send() {
    return httpClient.get(
        "/process-instances/" + processInstanceKey + "/statistics/element-instances",
        httpRequestConfig.build(),
        ProcessDefinitionElementStatisticsQueryResult.class,
        StatisticsResponseMapper::toProcessDefinitionStatisticsResponse,
        consistencyPolicy);
  }
}
