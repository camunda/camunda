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
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep3;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep4;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep5;
import io.camunda.client.api.response.CreateAgentInstanceResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateAgentInstanceResponseImpl;
import io.camunda.client.protocol.rest.AgentInstanceCreationRequest;
import io.camunda.client.protocol.rest.AgentInstanceCreationResult;
import io.camunda.client.protocol.rest.AgentInstanceHistoryRoleEnum;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
  }

  @Override
  public CreateAgentInstanceCommandStep2 elementInstanceKey(final long elementInstanceKey) {
    ArgumentUtil.ensureGreaterThan("elementInstanceKey", elementInstanceKey, 0);
    request.elementInstanceKey(String.valueOf(elementInstanceKey));
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep3 jobKey(final long jobKey) {
    ArgumentUtil.ensureGreaterThan("jobKey", jobKey, 0);
    request.jobKey(String.valueOf(jobKey));
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep4 jobLease(final String jobLease) {
    ArgumentUtil.ensureNotNull("jobLease", jobLease);
    if (jobLease.trim().isEmpty()) {
      throw new IllegalArgumentException("jobLease must not be blank");
    }
    request.jobLease(jobLease);
    return this;
  }

  @Override
  public CreateAgentInstanceCommandStep5 history(final List<AgentInstanceHistoryItem> history) {
    ArgumentUtil.ensureNotNull("history", history);
    if (history.isEmpty()) {
      throw new IllegalArgumentException("history must not be empty");
    }
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
  public CreateAgentInstanceCommandStep5 requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateAgentInstanceResponse> send() {
    ensureConfigurationEstablishesDefinition(request.getHistory());

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
