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
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.CreateAgentHistoryItemCommandStep2;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.CreateAgentHistoryItemCommandStep3;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.CreateAgentHistoryItemCommandStep4;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.CreateAgentHistoryItemCommandStep5;
import io.camunda.client.api.command.CreateAgentHistoryItemCommandStep1.CreateAgentHistoryItemFinalCommandStep;
import io.camunda.client.api.response.CreateAgentHistoryItemResponse;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateAgentHistoryItemResponseImpl;
import io.camunda.client.protocol.rest.AgentInstanceHistoryItemCreationResult;
import io.camunda.client.protocol.rest.AgentInstanceHistoryItemRequest;
import io.camunda.client.protocol.rest.AgentInstanceHistoryRoleEnum;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateAgentHistoryItemCommandImpl
    implements CreateAgentHistoryItemCommandStep1,
        CreateAgentHistoryItemCommandStep2,
        CreateAgentHistoryItemCommandStep3,
        CreateAgentHistoryItemCommandStep4,
        CreateAgentHistoryItemCommandStep5,
        CreateAgentHistoryItemFinalCommandStep {

  private final AgentInstanceHistoryItemRequest request;
  private final long agentInstanceKey;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public CreateAgentHistoryItemCommandImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long agentInstanceKey) {
    ArgumentUtil.ensureGreaterThan("agentInstanceKey", agentInstanceKey, 0);
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    this.agentInstanceKey = agentInstanceKey;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new AgentInstanceHistoryItemRequest();
  }

  @Override
  public CreateAgentHistoryItemCommandStep2 elementInstanceKey(final long elementInstanceKey) {
    ArgumentUtil.ensureGreaterThan("elementInstanceKey", elementInstanceKey, 0);
    request.elementInstanceKey(String.valueOf(elementInstanceKey));
    return this;
  }

  @Override
  public CreateAgentHistoryItemCommandStep3 jobKey(final long jobKey) {
    ArgumentUtil.ensureGreaterThan("jobKey", jobKey, 0);
    request.jobKey(String.valueOf(jobKey));
    return this;
  }

  @Override
  public CreateAgentHistoryItemCommandStep4 role(final AgentInstanceHistoryRole role) {
    ArgumentUtil.ensureNotNull("role", role);
    final AgentInstanceHistoryRoleEnum protoRole =
        AgentInstanceHistoryRoleEnum.fromValue(role.name());
    if (protoRole == null) {
      throw new IllegalArgumentException("Invalid role: " + role);
    }
    request.role(protoRole);
    return this;
  }

  @Override
  public CreateAgentHistoryItemCommandStep5 content(
      final List<AgentInstanceHistoryContent> content) {
    ArgumentUtil.ensureNotNull("content", content);
    request.content(AgentInstanceHistoryMapper.toProtocolContent(content));
    return this;
  }

  @Override
  public CreateAgentHistoryItemFinalCommandStep producedAt(final OffsetDateTime producedAt) {
    ArgumentUtil.ensureNotNull("producedAt", producedAt);
    request.producedAt(producedAt.toString());
    return this;
  }

  @Override
  public CreateAgentHistoryItemFinalCommandStep jobLease(final String jobLease) {
    ArgumentUtil.ensureNotNull("jobLease", jobLease);
    if (jobLease.trim().isEmpty()) {
      throw new IllegalArgumentException("jobLease must not be blank");
    }
    request.jobLease(jobLease);
    return this;
  }

  @Override
  public CreateAgentHistoryItemFinalCommandStep loopIteration(final int loopIteration) {
    ArgumentUtil.ensureGreaterThan("loopIteration", loopIteration, 0);
    request.loopIteration(loopIteration);
    return this;
  }

  @Override
  public CreateAgentHistoryItemFinalCommandStep toolCalls(
      final List<AgentInstanceHistoryToolCall> toolCalls) {
    if (toolCalls == null) {
      return this;
    }
    request.toolCalls(AgentInstanceHistoryMapper.toProtocolToolCalls(toolCalls));
    return this;
  }

  @Override
  public CreateAgentHistoryItemFinalCommandStep metrics(final AgentInstanceHistoryMetrics metrics) {
    if (metrics == null) {
      return this;
    }
    request.metrics(AgentInstanceHistoryMapper.toProtocolMetrics(metrics));
    return this;
  }

  @Override
  public CreateAgentHistoryItemFinalCommandStep requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateAgentHistoryItemResponse> send() {
    final HttpCamundaFuture<CreateAgentHistoryItemResponse> result = new HttpCamundaFuture<>();
    final CreateAgentHistoryItemResponseImpl response = new CreateAgentHistoryItemResponseImpl();
    httpClient.post(
        "/agent-instances/" + agentInstanceKey + "/history",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        AgentInstanceHistoryItemCreationResult.class,
        response::setResponse,
        result);
    return result;
  }
}
