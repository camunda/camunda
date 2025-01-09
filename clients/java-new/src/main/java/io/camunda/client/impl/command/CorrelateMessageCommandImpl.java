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

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CorrelateMessageCommandStep1;
import io.camunda.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep2;
import io.camunda.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep3;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CorrelateMessageResponseImpl;
import io.camunda.client.protocol.rest.MessageCorrelationRequest;
import io.camunda.client.protocol.rest.MessageCorrelationResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class CorrelateMessageCommandImpl extends CommandWithVariables<CorrelateMessageCommandImpl>
    implements CorrelateMessageCommandStep1,
        CorrelateMessageCommandStep2,
        CorrelateMessageCommandStep3 {

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final MessageCorrelationRequest request = new MessageCorrelationRequest();
  private final RequestConfig.Builder httpRequestConfig;

  public CorrelateMessageCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(jsonMapper);
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public CorrelateMessageCommandStep2 messageName(final String messageName) {
    request.setName(messageName);
    return this;
  }

  @Override
  public CorrelateMessageCommandStep3 tenantId(final String tenantId) {
    request.setTenantId(tenantId);
    return this;
  }

  @Override
  public CorrelateMessageCommandStep3 correlationKey(final String correlationKey) {
    request.setCorrelationKey(correlationKey);
    return this;
  }

  @Override
  public CorrelateMessageCommandStep3 withoutCorrelationKey() {
    return this;
  }

  @Override
  public FinalCommandStep<CorrelateMessageResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CorrelateMessageResponse> send() {
    final HttpCamundaFuture<CorrelateMessageResponse> result = new HttpCamundaFuture<>();
    final CorrelateMessageResponseImpl response = new CorrelateMessageResponseImpl(jsonMapper);
    httpClient.post(
        "/messages/correlation",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        MessageCorrelationResult.class,
        response::setResponse,
        result);
    return result;
  }

  @Override
  protected CorrelateMessageCommandImpl setVariablesInternal(final String variables) {
    request.setVariables(jsonMapper.fromJsonAsMap(variables));
    return this;
  }
}
