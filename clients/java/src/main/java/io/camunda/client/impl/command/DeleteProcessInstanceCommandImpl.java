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
import io.camunda.client.api.command.DeleteProcessInstanceCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.DeleteProcessInstanceResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.DeleteProcessInstanceResponseImpl;
import io.camunda.client.protocol.rest.DeleteProcessInstanceRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class DeleteProcessInstanceCommandImpl implements DeleteProcessInstanceCommandStep1 {

  private final long processInstanceKey;
  private final DeleteProcessInstanceRequest httpRequestObject;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public DeleteProcessInstanceCommandImpl(
      final long processInstanceKey,
      final CamundaClientConfiguration config,
      final HttpClient httpClient,
      final JsonMapper jsonMapper) {
    this.processInstanceKey = processInstanceKey;
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new DeleteProcessInstanceRequest();
    requestTimeout(config.getDefaultRequestTimeout());
  }

  @Override
  public DeleteProcessInstanceCommandStep1 operationReference(final long operationReference) {
    httpRequestObject.setOperationReference(operationReference);
    return this;
  }

  @Override
  public FinalCommandStep<DeleteProcessInstanceResponse> requestTimeout(
      final Duration requestTimeout) {
    ArgumentUtil.ensurePositive("requestTimeout", requestTimeout);
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<DeleteProcessInstanceResponse> send() {
    final HttpCamundaFuture<DeleteProcessInstanceResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/process-instances/" + processInstanceKey + "/deletion",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        DeleteProcessInstanceResponseImpl::new,
        result);
    return result;
  }
}
