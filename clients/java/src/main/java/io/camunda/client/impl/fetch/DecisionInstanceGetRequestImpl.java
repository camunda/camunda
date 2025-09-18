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
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.fetch.DecisionInstanceGetRequest;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.DecisionInstanceImpl;
import io.camunda.client.protocol.rest.DecisionInstanceGetQueryResult;

public class DecisionInstanceGetRequestImpl extends AbstractFetchRequestImpl<DecisionInstance>
    implements DecisionInstanceGetRequest {

  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final String decisionEvaluationInstanceKey;

  public DecisionInstanceGetRequestImpl(
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final String decisionEvaluationInstanceKey) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.decisionEvaluationInstanceKey = decisionEvaluationInstanceKey;
  }

  @Override
  public CamundaFuture<DecisionInstance> send() {
    return httpClient.get(
        String.format("/decision-instances/%s", decisionEvaluationInstanceKey),
        httpRequestConfig.build(),
        DecisionInstanceGetQueryResult.class,
        resp -> new DecisionInstanceImpl(resp, jsonMapper),
        consistencyPolicy);
  }
}
