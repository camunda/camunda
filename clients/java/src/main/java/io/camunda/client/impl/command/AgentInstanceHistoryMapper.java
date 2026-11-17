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

import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryContent.DocumentContent;
import io.camunda.client.api.command.AgentInstanceHistoryContent.ObjectContent;
import io.camunda.client.api.command.AgentInstanceHistoryContent.TextContent;
import io.camunda.client.api.command.AgentInstanceHistoryItem;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.command.AgentInstanceLimits;
import io.camunda.client.api.command.AgentTool;
import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.protocol.rest.AgentInstanceDocumentContent;
import io.camunda.client.protocol.rest.AgentInstanceHistoryItemMetricsRequest;
import io.camunda.client.protocol.rest.AgentInstanceHistoryRoleEnum;
import io.camunda.client.protocol.rest.AgentInstanceMessageContent;
import io.camunda.client.protocol.rest.AgentInstanceObjectContent;
import io.camunda.client.protocol.rest.AgentInstanceTextContent;
import io.camunda.client.protocol.rest.AgentInstanceToolCall;
import io.camunda.client.protocol.rest.DocumentMetadataResponse;
import io.camunda.client.protocol.rest.DocumentReference;
import io.camunda.client.protocol.rest.DocumentReference.CamundaDocumentTypeEnum;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates and maps the conversation-history sub-structures (content blocks, tool calls, metrics,
 * and whole history items) shared between {@link CreateAgentInstanceCommandImpl} and {@link
 * UpdateAgentInstanceCommandImpl} (each submitting a batch of history items). Both commands submit
 * items built from the same {@link AgentInstanceHistoryContent}/{@link
 * AgentInstanceHistoryToolCall}/ {@link AgentInstanceHistoryMetrics} API types onto the same
 * protocol wire types, so this class keeps their validation rules and error messages in one place
 * rather than several.
 */
final class AgentInstanceHistoryMapper {

  private AgentInstanceHistoryMapper() {}

  static List<AgentInstanceMessageContent> toProtocolContent(
      final List<AgentInstanceHistoryContent> content) {
    final List<AgentInstanceMessageContent> protocolContent = new ArrayList<>(content.size());
    for (final AgentInstanceHistoryContent item : content) {
      protocolContent.add(toProtocolContentItem(item));
    }
    return protocolContent;
  }

  private static AgentInstanceMessageContent toProtocolContentItem(
      final AgentInstanceHistoryContent item) {
    if (item == null) {
      throw new IllegalArgumentException("content must not contain null elements");
    }
    if (item instanceof TextContent) {
      final String text = ((TextContent) item).getText();
      if (text == null || text.trim().isEmpty()) {
        throw new IllegalArgumentException("text content value must not be null or blank");
      }
      return new AgentInstanceTextContent().text(text);
    }
    if (item instanceof ObjectContent) {
      final Object obj = ((ObjectContent) item).getObject();
      if (obj == null) {
        throw new IllegalArgumentException("object content value must not be null");
      }
      return new AgentInstanceObjectContent()._object(obj);
    }
    if (item instanceof DocumentContent) {
      return toProtocolDocumentContent((DocumentContent) item);
    }
    throw new IllegalArgumentException("Unsupported AgentInstanceHistoryContent type: " + item);
  }

  private static AgentInstanceDocumentContent toProtocolDocumentContent(
      final DocumentContent item) {
    final DocumentReferenceResponse ref = item.getDocumentReference();
    if (ref == null) {
      throw new IllegalArgumentException("documentReference must not be null");
    }
    if (ref.getDocumentId() == null || ref.getDocumentId().trim().isEmpty()) {
      throw new IllegalArgumentException("documentReference.documentId must not be null or blank");
    }
    final DocumentReference protocolRef =
        new DocumentReference()
            .camundaDocumentType(CamundaDocumentTypeEnum.CAMUNDA)
            .documentId(ref.getDocumentId())
            .storeId(ref.getStoreId());
    if (ref.getContentHash() != null) {
      protocolRef.contentHash(ref.getContentHash());
    }
    final DocumentMetadata metadata = ref.getMetadata();
    if (metadata != null) {
      protocolRef.metadata(toProtocolDocumentMetadata(metadata));
    }
    return new AgentInstanceDocumentContent().documentReference(protocolRef);
  }

  private static DocumentMetadataResponse toProtocolDocumentMetadata(
      final DocumentMetadata metadata) {
    final DocumentMetadataResponse protocolMeta = new DocumentMetadataResponse();
    if (metadata.getFileName() != null) {
      protocolMeta.fileName(metadata.getFileName());
    }
    if (metadata.getContentType() != null) {
      protocolMeta.contentType(metadata.getContentType());
    }
    if (metadata.getSize() != null) {
      protocolMeta.size(metadata.getSize());
    }
    if (metadata.getExpiresAt() != null) {
      protocolMeta.expiresAt(metadata.getExpiresAt().toString());
    }
    if (metadata.getProcessDefinitionId() != null) {
      protocolMeta.processDefinitionId(metadata.getProcessDefinitionId());
    }
    if (metadata.getProcessInstanceKey() != null) {
      protocolMeta.processInstanceKey(String.valueOf(metadata.getProcessInstanceKey()));
    }
    if (metadata.getCustomProperties() != null && !metadata.getCustomProperties().isEmpty()) {
      protocolMeta.customProperties(metadata.getCustomProperties());
    }
    return protocolMeta;
  }

  static List<AgentInstanceToolCall> toProtocolToolCalls(
      final List<AgentInstanceHistoryToolCall> toolCalls) {
    if (toolCalls == null) {
      return null;
    }
    final List<AgentInstanceToolCall> protocolToolCalls = new ArrayList<>(toolCalls.size());
    for (final AgentInstanceHistoryToolCall tc : toolCalls) {
      if (tc == null) {
        throw new IllegalArgumentException("toolCalls must not contain null elements");
      }
      if (tc.getToolCallId() == null || tc.getToolCallId().trim().isEmpty()) {
        throw new IllegalArgumentException("toolCallId must not be null or blank");
      }
      if (tc.getToolName() == null || tc.getToolName().trim().isEmpty()) {
        throw new IllegalArgumentException("toolName must not be null or blank");
      }
      protocolToolCalls.add(
          new AgentInstanceToolCall()
              .toolCallId(tc.getToolCallId())
              .toolName(tc.getToolName())
              .elementId(tc.getElementId())
              .arguments(tc.getArguments()));
    }
    return protocolToolCalls;
  }

  static AgentInstanceHistoryItemMetricsRequest toProtocolMetrics(
      final AgentInstanceHistoryMetrics metrics) {
    if (metrics == null) {
      return null;
    }
    return new AgentInstanceHistoryItemMetricsRequest()
        .inputTokens(metrics.getInputTokens())
        .outputTokens(metrics.getOutputTokens())
        .reasoningTokenCount(metrics.getReasoningTokenCount())
        .cacheCreationTokenCount(metrics.getCacheCreationTokenCount())
        .cacheReadTokenCount(metrics.getCacheReadTokenCount())
        .durationMs(metrics.getDurationMs());
  }

  static List<io.camunda.client.protocol.rest.AgentTool> toProtocolTools(
      final List<AgentTool> tools) {
    if (tools == null) {
      return null;
    }
    final List<io.camunda.client.protocol.rest.AgentTool> protocolTools =
        new ArrayList<>(tools.size());
    for (final AgentTool tool : tools) {
      if (tool == null) {
        throw new IllegalArgumentException("tools must not contain null elements");
      }
      final io.camunda.client.protocol.rest.AgentTool protocolTool =
          new io.camunda.client.protocol.rest.AgentTool();
      protocolTool.name(tool.getName());
      if (tool.getDescription() != null) {
        protocolTool.description(tool.getDescription());
      }
      if (tool.getElementId() != null) {
        protocolTool.elementId(tool.getElementId());
      }
      protocolTools.add(protocolTool);
    }
    return protocolTools;
  }

  static io.camunda.client.protocol.rest.AgentInstanceLimits toProtocolLimits(
      final AgentInstanceLimits limits) {
    if (limits == null) {
      return null;
    }
    if (limits.getMaxTokens() < -1) {
      throw new IllegalArgumentException("maxTokens must be >= -1");
    }
    if (limits.getMaxModelCalls() < -1) {
      throw new IllegalArgumentException("maxModelCalls must be >= -1");
    }
    if (limits.getMaxToolCalls() < -1) {
      throw new IllegalArgumentException("maxToolCalls must be >= -1");
    }
    return new io.camunda.client.protocol.rest.AgentInstanceLimits()
        .maxTokens(limits.getMaxTokens())
        .maxModelCalls(limits.getMaxModelCalls())
        .maxToolCalls(limits.getMaxToolCalls());
  }

  static io.camunda.client.protocol.rest.AgentInstanceHistoryItem toProtocolHistoryItem(
      final AgentInstanceHistoryItem item) {
    ArgumentUtil.ensureNotNull("historyItemId", item.getHistoryItemId());
    if (item.getHistoryItemId().trim().isEmpty()) {
      throw new IllegalArgumentException("historyItemId must not be blank");
    }
    ArgumentUtil.ensureGreaterThan("loopIteration", item.getLoopIteration(), 0);
    ArgumentUtil.ensureNotNull("role", item.getRole());
    ArgumentUtil.ensureNotNull("content", item.getContent());
    ArgumentUtil.ensureNotNull("producedAt", item.getProducedAt());

    final AgentInstanceHistoryRoleEnum protoRole =
        AgentInstanceHistoryRoleEnum.fromValue(item.getRole().name());
    if (protoRole == null) {
      throw new IllegalArgumentException("Invalid role: " + item.getRole());
    }

    return new io.camunda.client.protocol.rest.AgentInstanceHistoryItem()
        .historyItemId(item.getHistoryItemId())
        .loopIteration(item.getLoopIteration())
        .role(protoRole)
        .content(toProtocolContent(item.getContent()))
        .toolCalls(toProtocolToolCalls(item.getToolCalls()))
        .metrics(toProtocolMetrics(item.getMetrics()))
        .producedAt(item.getProducedAt().toString())
        .tools(toProtocolTools(item.getTools()))
        .model(item.getModel())
        .provider(item.getProvider())
        .limits(toProtocolLimits(item.getLimits()))
        .systemPrompt(
            item.getSystemPrompt() != null ? toProtocolContent(item.getSystemPrompt()) : null);
  }
}
