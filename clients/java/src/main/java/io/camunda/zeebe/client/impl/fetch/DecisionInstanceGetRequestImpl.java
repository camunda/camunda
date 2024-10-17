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
package io.camunda.zeebe.client.impl.fetch;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.fetch.DecisionInstanceGetRequest;
import io.camunda.zeebe.client.api.search.response.DecisionInstance;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.search.response.DecisionInstanceImpl;
import io.camunda.zeebe.client.protocol.rest.DecisionInstanceGetQueryResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class DecisionInstanceGetRequestImpl implements DecisionInstanceGetRequest {

  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long decisionInstanceKey;

  public DecisionInstanceGetRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long decisionInstanceKey) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    this.decisionInstanceKey = decisionInstanceKey;
  }

  @Override
  public FinalCommandStep<DecisionInstance> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<DecisionInstance> send() {
    final HttpZeebeFuture<DecisionInstance> result = new HttpZeebeFuture<>();
    httpClient.get(
        String.format("/decision-instances/%d", decisionInstanceKey),
        httpRequestConfig.build(),
        DecisionInstanceGetQueryResponse.class,
        resp -> new DecisionInstanceImpl(resp, jsonMapper),
        result);
    return result;
  }
}
