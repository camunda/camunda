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
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.AgentTool;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.HistoryItem;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.UpdateAgentInstanceCommandStep2;
import io.camunda.client.api.response.UpdateAgentInstanceResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.UpdateAgentInstanceResponseImpl;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.AgentInstanceHistoryItem;
import io.camunda.client.protocol.rest.AgentInstanceHistoryRoleEnum;
import io.camunda.client.protocol.rest.AgentInstanceMetricsDelta;
import io.camunda.client.protocol.rest.AgentInstanceUpdateRequest;
import io.camunda.client.protocol.rest.AgentInstanceUpdateResult;
import io.camunda.client.protocol.rest.AgentInstanceUpdateStatusEnum;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.config.RequestConfig;

public class UpdateAgentInstanceCommandImpl
    implements UpdateAgentInstanceCommandStep1, UpdateAgentInstanceCommandStep2 {

  private final AgentInstanceUpdateRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long agentInstanceKey;

  public UpdateAgentInstanceCommandImpl(
      final long agentInstanceKey, final HttpClient httpClient, final JsonMapper jsonMapper) {
    ArgumentUtil.ensureGreaterThan("agentInstanceKey", agentInstanceKey, 0);
    this.agentInstanceKey = agentInstanceKey;
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new AgentInstanceUpdateRequest();
  }

  @Override
  public UpdateAgentInstanceCommandStep2 elementInstanceKey(final long elementInstanceKey) {
    ArgumentUtil.ensureGreaterThan("elementInstanceKey", elementInstanceKey, 0);
    request.elementInstanceKey(String.valueOf(elementInstanceKey));
    return this;
  }

  @Override
  public UpdateAgentInstanceCommandStep2 status(final AgentInstanceUpdateStatus status) {
    request.status(EnumUtil.convert(status, AgentInstanceUpdateStatusEnum.class));
    return this;
  }

  @Override
  public UpdateAgentInstanceCommandStep2 inputTokens(final long inputTokens) {
    ensureMetrics().inputTokens(inputTokens);
    return this;
  }

  @Override
  public UpdateAgentInstanceCommandStep2 outputTokens(final long outputTokens) {
    ensureMetrics().outputTokens(outputTokens);
    return this;
  }

  @Override
  public UpdateAgentInstanceCommandStep2 modelCalls(final int modelCalls) {
    ensureMetrics().modelCalls(modelCalls);
    return this;
  }

  @Override
  public UpdateAgentInstanceCommandStep2 toolCalls(final int toolCalls) {
    ensureMetrics().toolCalls(toolCalls);
    return this;
  }

  @Override
  public UpdateAgentInstanceCommandStep2 tools(final List<AgentTool> tools) {
    final List<io.camunda.client.protocol.rest.AgentTool> protocolTools =
        tools.stream()
            .map(
                apiTool -> {
                  final io.camunda.client.protocol.rest.AgentTool protocolTool =
                      new io.camunda.client.protocol.rest.AgentTool();
                  protocolTool.name(apiTool.getName());
                  if (apiTool.getDescription() != null) {
                    protocolTool.description(apiTool.getDescription());
                  }
                  if (apiTool.getElementId() != null) {
                    protocolTool.elementId(apiTool.getElementId());
                  }
                  return protocolTool;
                })
            .collect(Collectors.toList());
    request.tools(protocolTools);
    return this;
  }

  @Override
  public UpdateAgentInstanceCommandStep2 jobKey(final long jobKey) {
    ArgumentUtil.ensureGreaterThan("jobKey", jobKey, 0);
    request.jobKey(String.valueOf(jobKey));
    return this;
  }

  @Override
  public UpdateAgentInstanceCommandStep2 jobLease(final String jobLease) {
    ArgumentUtil.ensureNotNull("jobLease", jobLease);
    if (jobLease.trim().isEmpty()) {
      throw new IllegalArgumentException("jobLease must not be blank");
    }
    request.jobLease(jobLease);
    return this;
  }

  @Override
  public UpdateAgentInstanceCommandStep2 history(final List<HistoryItem> history) {
    ArgumentUtil.ensureNotNull("history", history);
    final List<AgentInstanceHistoryItem> protocolHistory = new ArrayList<>(history.size());
    for (final HistoryItem item : history) {
      if (item == null) {
        throw new IllegalArgumentException("history must not contain null elements");
      }
      protocolHistory.add(toProtocolHistoryItem(item));
    }
    request.history(protocolHistory);
    return this;
  }

  private AgentInstanceHistoryItem toProtocolHistoryItem(final HistoryItem item) {
    ArgumentUtil.ensureNotNull("historyItemId", item.getHistoryItemId());
    if (item.getHistoryItemId().trim().isEmpty()) {
      throw new IllegalArgumentException("historyItemId must not be blank");
    }
    ArgumentUtil.ensureGreaterThan("loopIteration", item.getLoopIteration(), 0);
    ArgumentUtil.ensureNotNull("role", item.getRole());
    ArgumentUtil.ensureNotNull("content", item.getContent());
    if (item.getContent().isEmpty()) {
      throw new IllegalArgumentException("content must not be empty");
    }
    ArgumentUtil.ensureNotNull("producedAt", item.getProducedAt());

    final AgentInstanceHistoryRoleEnum protoRole =
        AgentInstanceHistoryRoleEnum.fromValue(item.getRole().name());
    if (protoRole == null) {
      throw new IllegalArgumentException("Invalid role: " + item.getRole());
    }

    return new AgentInstanceHistoryItem()
        .historyItemId(item.getHistoryItemId())
        .loopIteration(item.getLoopIteration())
        .role(protoRole)
        .content(AgentInstanceHistoryMapper.toProtocolContent(item.getContent()))
        .toolCalls(AgentInstanceHistoryMapper.toProtocolToolCalls(item.getToolCalls()))
        .metrics(AgentInstanceHistoryMapper.toProtocolMetrics(item.getMetrics()))
        .producedAt(item.getProducedAt().toString());
  }

  @Override
  public UpdateAgentInstanceCommandStep2 requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UpdateAgentInstanceResponse> send() {
    if (request.getHistory() != null
        && !request.getHistory().isEmpty()
        && request.getJobKey() == null) {
      throw new IllegalArgumentException("jobKey must be set when history is not empty");
    }
    final HttpCamundaFuture<UpdateAgentInstanceResponse> result = new HttpCamundaFuture<>();
    httpClient.patch(
        "/agent-instances/" + agentInstanceKey,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        AgentInstanceUpdateResult.class,
        UpdateAgentInstanceResponseImpl::new,
        result);
    return result;
  }

  private AgentInstanceMetricsDelta ensureMetrics() {
    if (request.getMetrics() == null) {
      request.metrics(new AgentInstanceMetricsDelta());
    }
    return request.getMetrics();
  }
}
