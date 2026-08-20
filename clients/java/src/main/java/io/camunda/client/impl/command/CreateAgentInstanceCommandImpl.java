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
import io.camunda.client.api.command.AgentInstanceHistoryItem;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep2;
import io.camunda.client.api.response.CreateAgentInstanceResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateAgentInstanceResponseImpl;
import io.camunda.client.protocol.rest.AgentInstanceCreationRequest;
import io.camunda.client.protocol.rest.AgentInstanceCreationResult;
import io.camunda.client.protocol.rest.AgentInstanceDefinition;
import io.camunda.client.protocol.rest.AgentInstanceHistoryRoleEnum;
import io.camunda.client.protocol.rest.AgentInstanceLimits;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateAgentInstanceCommandImpl
    implements CreateAgentInstanceCommandStep1, CreateAgentInstanceCommandStep2 {

  private final AgentInstanceCreationRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateAgentInstanceCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new AgentInstanceCreationRequest();
  }

  @Override
  public CreateAgentInstanceCommandStep2 elementInstanceKey(final long elementInstanceKey) {
    ArgumentUtil.ensureGreaterThan("elementInstanceKey", elementInstanceKey, 0);
    request.elementInstanceKey(String.valueOf(elementInstanceKey));
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep2 model(final String model) {
    ArgumentUtil.ensureNotNullNorEmpty("model", model);
    ensureDefinition().model(model);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep2 provider(final String provider) {
    ArgumentUtil.ensureNotNullNorEmpty("provider", provider);
    ensureDefinition().provider(provider);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep2 systemPrompt(final String systemPrompt) {
    ArgumentUtil.ensureNotNullNorEmpty("systemPrompt", systemPrompt);
    ensureDefinition().systemPrompt(systemPrompt);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep2 maxTokens(final long maxTokens) {
    ArgumentUtil.ensureGreaterThan("maxTokens", maxTokens, -2);
    ensureLimits().maxTokens(maxTokens);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep2 maxModelCalls(final int maxModelCalls) {
    ArgumentUtil.ensureGreaterThan("maxModelCalls", maxModelCalls, -2);
    ensureLimits().maxModelCalls(maxModelCalls);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep2 maxToolCalls(final int maxToolCalls) {
    ArgumentUtil.ensureGreaterThan("maxToolCalls", maxToolCalls, -2);
    ensureLimits().maxToolCalls(maxToolCalls);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep2 jobKey(final long jobKey) {
    ArgumentUtil.ensureGreaterThan("jobKey", jobKey, 0);
    request.jobKey(String.valueOf(jobKey));
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep2 jobLease(final String jobLease) {
    ArgumentUtil.ensureNotNull("jobLease", jobLease);
    if (jobLease.trim().isEmpty()) {
      throw new IllegalArgumentException("jobLease must not be blank");
    }
    request.jobLease(jobLease);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep2 history(final List<AgentInstanceHistoryItem> history) {
    ArgumentUtil.ensureNotNull("history", history);
    final List<io.camunda.client.protocol.rest.AgentInstanceHistoryItem> protocolHistory =
        new ArrayList<>(history.size());
    for (final AgentInstanceHistoryItem item : history) {
      if (item == null) {
        throw new IllegalArgumentException("history must not contain null elements");
      }
      protocolHistory.add(AgentInstanceHistoryMapper.toProtocolHistoryItem(item));
    }
    request.history(protocolHistory);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep2 requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateAgentInstanceResponse> send() {
    final boolean hasHistory = request.getHistory() != null && !request.getHistory().isEmpty();

    if (hasHistory) {
      if (request.getJobKey() == null) {
        throw new IllegalArgumentException("jobKey must be set when history is not empty");
      }
      if (request.getDefinition() != null) {
        throw new IllegalArgumentException(
            "model, provider, and systemPrompt must not be set when history is not empty");
      }
      if (request.getLimits() != null) {
        throw new IllegalArgumentException(
            "maxTokens, maxModelCalls, and maxToolCalls must not be set when history is not"
                + " empty");
      }
      ensureConfigurationEstablishesDefinition(request.getHistory());
    } else if (request.getDefinition() == null
        || request.getDefinition().getModel() == null
        || request.getDefinition().getProvider() == null
        || request.getDefinition().getSystemPrompt() == null) {
      throw new IllegalArgumentException(
          "model, provider, and systemPrompt must be set when history is empty");
    }

    final HttpCamundaFuture<CreateAgentInstanceResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/agent-instances",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        AgentInstanceCreationResult.class,
        CreateAgentInstanceResponseImpl::new,
        result);
    return result;
  }

  private AgentInstanceDefinition ensureDefinition() {
    if (request.getDefinition() == null) {
      request.definition(new AgentInstanceDefinition());
    }
    return request.getDefinition();
  }

  private AgentInstanceLimits ensureLimits() {
    if (request.getLimits() == null) {
      request.limits(new AgentInstanceLimits());
    }
    return request.getLimits();
  }

  private void ensureConfigurationEstablishesDefinition(
      final List<io.camunda.client.protocol.rest.AgentInstanceHistoryItem> history) {
    final List<io.camunda.client.protocol.rest.AgentInstanceHistoryItem> configurationItems =
        history.stream()
            .filter(
                item ->
                    item != null && item.getRole() == AgentInstanceHistoryRoleEnum.CONFIGURATION)
            .collect(Collectors.toList());

    final boolean hasModel =
        configurationItems.stream()
            .anyMatch(item -> item.getModel() != null && !item.getModel().trim().isEmpty());
    final boolean hasProvider =
        configurationItems.stream()
            .anyMatch(item -> item.getProvider() != null && !item.getProvider().trim().isEmpty());
    final boolean hasSystemPrompt =
        configurationItems.stream()
            .anyMatch(item -> item.getSystemPrompt() != null && !item.getSystemPrompt().isEmpty());

    if (!hasModel || !hasProvider || !hasSystemPrompt) {
      throw new IllegalArgumentException(
          "history must include a CONFIGURATION item establishing model, provider, and"
              + " systemPrompt when history is not empty");
    }
  }
}
