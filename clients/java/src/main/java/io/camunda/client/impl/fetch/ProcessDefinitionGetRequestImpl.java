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
package io.camunda.client.impl.fetch;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.fetch.ProcessDefinitionGetRequest;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.ProcessDefinitionResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class ProcessDefinitionGetRequestImpl implements ProcessDefinitionGetRequest {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long processDefinitionKey;

  public ProcessDefinitionGetRequestImpl(
      final HttpClient httpClient, final long processDefinitionKey) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    this.processDefinitionKey = processDefinitionKey;
  }

  @Override
  public FinalCommandStep<ProcessDefinition> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<ProcessDefinition> send() {
    final HttpCamundaFuture<ProcessDefinition> result = new HttpCamundaFuture<>();
    httpClient.get(
        String.format("/process-definitions/%d", processDefinitionKey),
        httpRequestConfig.build(),
        ProcessDefinitionResult.class,
        SearchResponseMapper::toProcessDefinitionGetResponse,
        result);
    return result;
  }
}
