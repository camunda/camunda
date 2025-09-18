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
import io.camunda.client.api.fetch.ProcessDefinitionGetRequest;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.ProcessDefinitionResult;

public class ProcessDefinitionGetRequestImpl extends AbstractFetchRequestImpl<ProcessDefinition>
    implements ProcessDefinitionGetRequest {

  private final HttpClient httpClient;
  private final long processDefinitionKey;

  public ProcessDefinitionGetRequestImpl(
      final HttpClient httpClient, final long processDefinitionKey) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.processDefinitionKey = processDefinitionKey;
  }

  @Override
  public CamundaFuture<ProcessDefinition> send() {
    return httpClient.get(
        String.format("/process-definitions/%d", processDefinitionKey),
        httpRequestConfig.build(),
        ProcessDefinitionResult.class,
        SearchResponseMapper::toProcessDefinitionGetResponse,
        consistencyPolicy);
  }
}
