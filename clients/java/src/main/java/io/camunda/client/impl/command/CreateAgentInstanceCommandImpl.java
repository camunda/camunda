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
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep2;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep3;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep4;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep5;
import io.camunda.client.api.response.CreateAgentInstanceResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateAgentInstanceResponseImpl;
import io.camunda.client.protocol.rest.AgentInstanceCreationRequest;
import io.camunda.client.protocol.rest.AgentInstanceCreationResult;
import io.camunda.client.protocol.rest.AgentInstanceDefinition;
import io.camunda.client.protocol.rest.AgentInstanceLimits;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateAgentInstanceCommandImpl
    implements CreateAgentInstanceCommandStep1,
        CreateAgentInstanceCommandStep2,
        CreateAgentInstanceCommandStep3,
        CreateAgentInstanceCommandStep4,
        CreateAgentInstanceCommandStep5 {

  private final AgentInstanceCreationRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateAgentInstanceCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new AgentInstanceCreationRequest();
    request.definition(new AgentInstanceDefinition());
  }

  @Override
  public CreateAgentInstanceCommandStep2 elementInstanceKey(final long elementInstanceKey) {
    ArgumentUtil.ensureGreaterThan("elementInstanceKey", elementInstanceKey, 0);
    request.elementInstanceKey(String.valueOf(elementInstanceKey));
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep3 model(final String model) {
    ArgumentUtil.ensureNotNullNorEmpty("model", model);
    request.getDefinition().model(model);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep4 provider(final String provider) {
    ArgumentUtil.ensureNotNullNorEmpty("provider", provider);
    request.getDefinition().provider(provider);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep5 systemPrompt(final String systemPrompt) {
    ArgumentUtil.ensureNotNullNorEmpty("systemPrompt", systemPrompt);
    request.getDefinition().systemPrompt(systemPrompt);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep5 maxTokens(final long maxTokens) {
    ensureLimits().maxTokens(maxTokens);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep5 maxModelCalls(final int maxModelCalls) {
    ensureLimits().maxModelCalls(maxModelCalls);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep5 maxToolCalls(final int maxToolCalls) {
    ensureLimits().maxToolCalls(maxToolCalls);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep5 requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateAgentInstanceResponse> send() {
    final HttpCamundaFuture<CreateAgentInstanceResponse> result = new HttpCamundaFuture<>();
    final CreateAgentInstanceResponseImpl response = new CreateAgentInstanceResponseImpl();
    httpClient.post(
        "/agent-instances",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        AgentInstanceCreationResult.class,
        response::setResponse,
        result);
    return result;
  }

  private AgentInstanceLimits ensureLimits() {
    if (request.getLimits() == null) {
      request.limits(new AgentInstanceLimits());
    }
    return request.getLimits();
  }
}
