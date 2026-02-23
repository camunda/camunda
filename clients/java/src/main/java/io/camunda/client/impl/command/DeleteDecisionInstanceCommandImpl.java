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
package io.camunda.client.impl.command;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.DeleteDecisionInstanceCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.DeleteDecisionInstanceResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.DeleteDecisionInstanceResponseImpl;
import io.camunda.client.protocol.rest.DeleteDecisionInstanceRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class DeleteDecisionInstanceCommandImpl implements DeleteDecisionInstanceCommandStep1 {

  private final long decisionInstanceKey;
  private final DeleteDecisionInstanceRequest httpRequestObject;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public DeleteDecisionInstanceCommandImpl(
      final long decisionInstanceKey,
      final CamundaClientConfiguration config,
      final HttpClient httpClient,
      final JsonMapper jsonMapper) {
    this.decisionInstanceKey = decisionInstanceKey;
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new DeleteDecisionInstanceRequest();
    requestTimeout(config.getDefaultRequestTimeout());
  }

  @Override
  public DeleteDecisionInstanceCommandStep1 operationReference(final long operationReference) {
    httpRequestObject.setOperationReference(operationReference);
    return this;
  }

  @Override
  public FinalCommandStep<DeleteDecisionInstanceResponse> requestTimeout(
      final Duration requestTimeout) {
    ArgumentUtil.ensurePositive("requestTimeout", requestTimeout);
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<DeleteDecisionInstanceResponse> send() {
    final HttpCamundaFuture<DeleteDecisionInstanceResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/decision-instances/" + decisionInstanceKey + "/deletion",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        DeleteDecisionInstanceResponseImpl::new,
        result);
    return result;
  }
}
