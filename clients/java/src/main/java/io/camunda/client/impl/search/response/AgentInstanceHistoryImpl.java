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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryContent.DocumentContent;
import io.camunda.client.api.command.AgentInstanceHistoryContent.ObjectContent;
import io.camunda.client.api.command.AgentInstanceHistoryContent.TextContent;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.search.enums.AgentInstanceHistoryCommitStatus;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.response.AgentInstanceHistory;
import io.camunda.client.impl.response.DocumentReferenceResponseImpl;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.AgentInstanceDocumentContent;
import io.camunda.client.protocol.rest.AgentInstanceHistoryItemMetrics;
import io.camunda.client.protocol.rest.AgentInstanceHistoryItemResult;
import io.camunda.client.protocol.rest.AgentInstanceMessageContent;
import io.camunda.client.protocol.rest.AgentInstanceObjectContent;
import io.camunda.client.protocol.rest.AgentInstanceTextContent;
import io.camunda.client.protocol.rest.AgentInstanceToolCall;
import io.camunda.client.protocol.rest.DocumentReference;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AgentInstanceHistoryImpl implements AgentInstanceHistory {

  private final long historyItemKey;
  private final long agentInstanceKey;
  private final long elementInstanceKey;
  private final long jobKey;
  private final String jobLease;
  private final Integer iteration;
  private final AgentInstanceHistoryRole role;
  private final List<AgentInstanceHistoryContent> content;
  private final List<AgentInstanceHistoryToolCall> toolCalls;
  private final AgentInstanceHistoryMetrics metrics;
  private final AgentInstanceHistoryCommitStatus commitStatus;
  private final OffsetDateTime producedAt;

  public AgentInstanceHistoryImpl(final AgentInstanceHistoryItemResult result) {
    historyItemKey = Long.parseLong(result.getHistoryItemKey());
    agentInstanceKey = Long.parseLong(result.getAgentInstanceKey());
    elementInstanceKey = Long.parseLong(result.getElementInstanceKey());
    jobKey = Long.parseLong(result.getJobKey());
    jobLease = result.getJobLease();
    iteration = result.getIteration();
    role = EnumUtil.convert(result.getRole(), AgentInstanceHistoryRole.class);
    content =
        result.getContent() != null
            ? result.getContent().stream()
                .map(AgentInstanceHistoryImpl::toContent)
                .collect(Collectors.toList())
            : Collections.emptyList();
    toolCalls =
        result.getToolCalls() != null
            ? result.getToolCalls().stream()
                .map(AgentInstanceHistoryImpl::toToolCall)
                .collect(Collectors.toList())
            : Collections.emptyList();
    metrics = toMetrics(result.getMetrics());
    commitStatus =
        EnumUtil.convert(result.getCommitStatus(), AgentInstanceHistoryCommitStatus.class);
    producedAt = ParseUtil.parseOffsetDateTimeOrNull(result.getProducedAt());
  }

  @Override
  public long getHistoryItemKey() {
    return historyItemKey;
  }

  @Override
  public long getAgentInstanceKey() {
    return agentInstanceKey;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public long getJobKey() {
    return jobKey;
  }

  @Override
  public String getJobLease() {
    return jobLease;
  }

  @Override
  public Integer getIteration() {
    return iteration;
  }

  @Override
  public AgentInstanceHistoryRole getRole() {
    return role;
  }

  @Override
  public List<AgentInstanceHistoryContent> getContent() {
    return content;
  }

  @Override
  public List<AgentInstanceHistoryToolCall> getToolCalls() {
    return toolCalls;
  }

  @Override
  public AgentInstanceHistoryMetrics getMetrics() {
    return metrics;
  }

  @Override
  public AgentInstanceHistoryCommitStatus getCommitStatus() {
    return commitStatus;
  }

  @Override
  public OffsetDateTime getProducedAt() {
    return producedAt;
  }

  private static AgentInstanceHistoryContent toContent(final AgentInstanceMessageContent proto) {
    if (proto instanceof AgentInstanceTextContent) {
      return new TextContent(((AgentInstanceTextContent) proto).getText());
    } else if (proto instanceof AgentInstanceDocumentContent) {
      final Object ref = ((AgentInstanceDocumentContent) proto).getDocumentReference();
      return new DocumentContent(
          ref != null ? new DocumentReferenceResponseImpl((DocumentReference) ref) : null);
    } else if (proto instanceof AgentInstanceObjectContent) {
      @SuppressWarnings("unchecked")
      final Map<String, Object> obj =
          (Map<String, Object>) ((AgentInstanceObjectContent) proto).getObject();
      return new ObjectContent(obj);
    }
    return new TextContent(proto.getContentType());
  }

  @SuppressWarnings("unchecked")
  private static AgentInstanceHistoryToolCall toToolCall(final AgentInstanceToolCall proto) {
    return new AgentInstanceHistoryToolCall()
        .toolCallId(proto.getToolCallId())
        .toolName(proto.getToolName())
        .elementId(proto.getElementId())
        .arguments(
            proto.getArguments() != null ? (Map<String, Object>) proto.getArguments() : null);
  }

  private static AgentInstanceHistoryMetrics toMetrics(
      final AgentInstanceHistoryItemMetrics proto) {
    if (proto == null) {
      return null;
    }
    return new AgentInstanceHistoryMetrics()
        .inputTokens(proto.getInputTokens())
        .outputTokens(proto.getOutputTokens())
        .durationMs(proto.getDurationMs());
  }
}
