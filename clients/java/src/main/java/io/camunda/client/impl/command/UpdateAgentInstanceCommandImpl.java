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
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.UpdateAgentInstanceCommandStep2;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.UpdateAgentInstanceCommandStep3;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.UpdateAgentInstanceCommandStep4;
import io.camunda.client.api.response.UpdateAgentInstanceResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.UpdateAgentInstanceResponseImpl;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.AgentInstanceUpdateRequest;
import io.camunda.client.protocol.rest.AgentInstanceUpdateResult;
import io.camunda.client.protocol.rest.AgentInstanceUpdateStatusEnum;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class UpdateAgentInstanceCommandImpl
    implements UpdateAgentInstanceCommandStep1,
        UpdateAgentInstanceCommandStep2,
        UpdateAgentInstanceCommandStep3,
        UpdateAgentInstanceCommandStep4 {

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
  public UpdateAgentInstanceCommandStep3 jobKey(final long jobKey) {
    ArgumentUtil.ensureGreaterThan("jobKey", jobKey, 0);
    request.jobKey(String.valueOf(jobKey));
    return this;
  }

  @Override
  public UpdateAgentInstanceCommandStep4 jobLease(final String jobLease) {
    ArgumentUtil.ensureNotNull("jobLease", jobLease);
    if (jobLease.trim().isEmpty()) {
      throw new IllegalArgumentException("jobLease must not be blank");
    }
    request.jobLease(jobLease);
    return this;
  }

  @Override
  public UpdateAgentInstanceCommandStep4 history(final List<AgentInstanceHistoryItem> history) {
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
  public UpdateAgentInstanceCommandStep4 requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UpdateAgentInstanceResponse> send() {
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
}
