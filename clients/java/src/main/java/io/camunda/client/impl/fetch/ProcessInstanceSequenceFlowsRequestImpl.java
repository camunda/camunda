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
import io.camunda.client.api.fetch.ProcessInstanceSequenceFlowsRequest;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.ProcessInstanceSequenceFlowsQueryResult;
import java.util.List;

public class ProcessInstanceSequenceFlowsRequestImpl
    extends AbstractFetchRequestImpl<List<ProcessInstanceSequenceFlow>>
    implements ProcessInstanceSequenceFlowsRequest {

  private final long processInstanceKey;
  private final HttpClient httpClient;

  public ProcessInstanceSequenceFlowsRequestImpl(
      final HttpClient httpClient, final long processInstanceKey) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public CamundaFuture<List<ProcessInstanceSequenceFlow>> send() {
    return httpClient.get(
        "/process-instances/" + processInstanceKey + "/sequence-flows",
        httpRequestConfig.build(),
        ProcessInstanceSequenceFlowsQueryResult.class,
        SearchResponseMapper::toProcessInstanceSequenceFlowSearchResponse,
        consistencyPolicy);
  }
}
