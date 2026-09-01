/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.mapping.http.validator.AgentInstanceRequestValidator;
import io.camunda.gateway.protocol.model.AgentInstanceCreatedHistoryItem;
import io.camunda.gateway.protocol.model.AgentInstanceCreationRequest;
import io.camunda.gateway.protocol.model.AgentInstanceCreationResult;
import io.camunda.gateway.protocol.model.AgentInstanceDocumentContent;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryItem;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryRoleEnum;
import io.camunda.gateway.protocol.model.AgentInstanceLimits;
import io.camunda.gateway.protocol.model.AgentInstanceMessageContent;
import io.camunda.gateway.protocol.model.AgentInstanceObjectContent;
import io.camunda.gateway.protocol.model.AgentInstanceTextContent;
import io.camunda.gateway.protocol.model.AgentInstanceToolCall;
import io.camunda.gateway.protocol.model.AgentInstanceUpdateRequest;
import io.camunda.gateway.protocol.model.AgentInstanceUpdateResult;
import io.camunda.gateway.protocol.model.AgentInstanceUpdateStatusEnum;
import io.camunda.gateway.protocol.model.AgentTool;
import io.camunda.gateway.protocol.model.DocumentMetadataResponse;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryEmbeddedToolCall;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceTool;
import io.camunda.zeebe.protocol.impl.record.value.document.DocumentReferenceMetadata;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ProblemDetail;

@NullMarked
public class AgentInstanceMapper {

  private final AgentInstanceRequestValidator requestValidator;

  public AgentInstanceMapper(final AgentInstanceRequestValidator requestValidator) {
    this.requestValidator = requestValidator;
  }

  public Either<ProblemDetail, AgentInstanceRecord> toCreateAgentInstanceRecord(
      final AgentInstanceCreationRequest request) {
    return RequestMapper.getResult(
        requestValidator.validateCreateRequest(request),
        () -> {
          final var record = new AgentInstanceRecord();

          record.setElementInstanceKey(Long.parseLong(request.getElementInstanceKey()));

          if (request.getJobKey() != null) {
            record.setJobKey(Long.parseLong(request.getJobKey()));
          }

          if (request.getJobLease() != null) {
            record.setJobLease(request.getJobLease());
          }

          if (request.getHistory() != null) {
            for (final AgentInstanceHistoryItem historyItem : request.getHistory()) {
              record.addHistoryItem(mapHistoryItem(historyItem));
            }
          }

          return record;
        });
  }

  public Either<ProblemDetail, AgentInstanceRecord> toUpdateAgentInstanceRecord(
      final String agentInstanceKey, final AgentInstanceUpdateRequest request) {
    return RequestMapper.getResult(
        requestValidator.validateUpdateRequest(agentInstanceKey, request),
        () -> {
          final var record = new AgentInstanceRecord();
          record.setAgentInstanceKey(Long.parseLong(agentInstanceKey));
          record.setElementInstanceKey(Long.parseLong(request.getElementInstanceKey()));

          if (request.getStatus() != null) {
            record.setStatus(mapStatus(request.getStatus()));
            record.addChangedAttribute("status");
          }

          if (request.getJobKey() != null) {
            record.setJobKey(Long.parseLong(request.getJobKey()));
          }

          if (request.getJobLease() != null) {
            record.setJobLease(request.getJobLease());
          }

          if (request.getHistory() != null) {
            for (final AgentInstanceHistoryItem historyItem : request.getHistory()) {
              record.addHistoryItem(mapHistoryItem(historyItem));
            }
          }

          return record;
        });
  }

  public AgentInstanceCreationResult toAgentInstanceCreationResult(
      final AgentInstanceRecord record) {
    return AgentInstanceCreationResult.Builder.create()
        .agentInstanceKey(KeyUtil.keyToString(record.getAgentInstanceKey()))
        .createdHistory(toCreatedHistory(record))
        .build();
  }

  public AgentInstanceUpdateResult toAgentInstanceUpdateResult(final AgentInstanceRecord record) {
    return AgentInstanceUpdateResult.Builder.create()
        .createdHistory(toCreatedHistory(record))
        .build();
  }

  private List<AgentInstanceCreatedHistoryItem> toCreatedHistory(final AgentInstanceRecord record) {
    return record.getHistory().stream()
        .map(
            item ->
                AgentInstanceCreatedHistoryItem.Builder.create()
                    .historyItemId(item.getHistoryItemId())
                    .historyItemKey(KeyUtil.keyToString(item.getAgentHistoryKey()))
                    .isDuplicate(item.isDuplicate())
                    .build())
        .collect(Collectors.toList());
  }

  private AgentHistoryRecord mapHistoryItem(final AgentInstanceHistoryItem historyItem) {
    final var record = new AgentHistoryRecord();

    record.setHistoryItemId(historyItem.getHistoryItemId());
    record.setLoopIteration(historyItem.getLoopIteration());
    record.setRole(mapHistoryRole(historyItem.getRole()));
    record.setProducedAt(
        OffsetDateTime.parse(historyItem.getProducedAt()).toInstant().toEpochMilli());

    for (final AgentInstanceMessageContent content : historyItem.getContent()) {
      record.addContent(mapContent(content));
    }

    if (historyItem.getToolCalls() != null) {
      for (final AgentInstanceToolCall toolCall : historyItem.getToolCalls()) {
        record.addToolCall(mapToolCall(toolCall));
      }
    }

    if (historyItem.getMetrics() != null) {
      final var metrics = historyItem.getMetrics();
      final var recordMetrics = record.getMetrics();
      if (metrics.getInputTokens() != null) {
        recordMetrics.setInputTokens(metrics.getInputTokens());
      }
      if (metrics.getOutputTokens() != null) {
        recordMetrics.setOutputTokens(metrics.getOutputTokens());
      }
      if (metrics.getReasoningTokenCount() != null) {
        recordMetrics.setReasoningTokenCount(metrics.getReasoningTokenCount());
      }
      if (metrics.getCacheCreationTokenCount() != null) {
        recordMetrics.setCacheCreationTokenCount(metrics.getCacheCreationTokenCount());
      }
      if (metrics.getCacheReadTokenCount() != null) {
        recordMetrics.setCacheReadTokenCount(metrics.getCacheReadTokenCount());
      }
      if (metrics.getDurationMs() != null) {
        recordMetrics.setDurationMs(metrics.getDurationMs());
      }
    }

    if (historyItem.getTools() != null) {
      final List<AgentInstanceTool> tools =
          historyItem.getTools().stream().map(this::mapTool).collect(Collectors.toList());
      record.setTools(tools);
      record.addChangedAttribute("tools");
    }

    if (historyItem.getModel() != null) {
      record.setModel(historyItem.getModel());
      record.addChangedAttribute("model");
    }

    if (historyItem.getProvider() != null) {
      record.setProvider(historyItem.getProvider());
      record.addChangedAttribute("provider");
    }

    if (historyItem.getLimits() != null) {
      final AgentInstanceLimits limits = historyItem.getLimits();
      fillLimits(limits, record.getLimits());
      if (limits.getMaxTokens() != null) {
        record.addChangedAttribute(AgentInstanceRecord.ATTR_MAX_TOKENS);
      }
      if (limits.getMaxModelCalls() != null) {
        record.addChangedAttribute(AgentInstanceRecord.ATTR_MAX_MODEL_CALLS);
      }
      if (limits.getMaxToolCalls() != null) {
        record.addChangedAttribute(AgentInstanceRecord.ATTR_MAX_TOOL_CALLS);
      }
    }

    if (historyItem.getSystemPrompt() != null) {
      for (final AgentInstanceMessageContent content : historyItem.getSystemPrompt()) {
        record.addSystemPrompt(mapContent(content));
      }
      record.addChangedAttribute("systemPrompt");
    }

    return record;
  }

  private AgentHistoryRole mapHistoryRole(final AgentInstanceHistoryRoleEnum role) {
    return switch (role) {
      case USER -> AgentHistoryRole.USER;
      case ASSISTANT -> AgentHistoryRole.ASSISTANT;
      case TOOL_RESULT -> AgentHistoryRole.TOOL_RESULT;
      case CONFIGURATION -> AgentHistoryRole.CONFIGURATION;
    };
  }

  private AgentHistoryMessageContent mapContent(final AgentInstanceMessageContent content) {
    final var result = new AgentHistoryMessageContent();
    if (content instanceof final AgentInstanceTextContent text) {
      result
          .setContentType(AgentHistoryContentType.TEXT)
          .setText(text.getText() != null ? text.getText() : "");
    } else if (content instanceof final AgentInstanceDocumentContent doc) {
      result.setContentType(AgentHistoryContentType.DOCUMENT);
      final var ref = doc.getDocumentReference();
      if (ref != null) {
        result
            .getDocumentReference()
            .setDocumentId(ref.getDocumentId() != null ? ref.getDocumentId() : "")
            .setStoreId(ref.getStoreId() != null ? ref.getStoreId() : "")
            .setContentHash(ref.getContentHash() != null ? ref.getContentHash() : "");
        final var meta = ref.getMetadata();
        if (meta != null) {
          fillDocumentReferenceMetadata(meta, result.getDocumentReference().getMetadata());
        }
      }
    } else if (content instanceof final AgentInstanceObjectContent obj) {
      result.setContentType(AgentHistoryContentType.OBJECT);
      if (obj.getObject() != null) {
        result.setObject(toMsgPackBuffer(obj.getObject()));
      }
    }
    return result;
  }

  private AgentHistoryEmbeddedToolCall mapToolCall(final AgentInstanceToolCall toolCall) {
    final var result = new AgentHistoryEmbeddedToolCall();
    result.setToolCallId(toolCall.getToolCallId() != null ? toolCall.getToolCallId() : "");
    result.setToolName(toolCall.getToolName() != null ? toolCall.getToolName() : "");
    if (toolCall.getElementId() != null) {
      result.setElementId(toolCall.getElementId());
    }
    if (toolCall.getArguments() != null) {
      result.setArguments(toMsgPackBuffer(toolCall.getArguments()));
    }
    return result;
  }

  private DirectBuffer toMsgPackBuffer(final Object value) {
    return BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(value));
  }

  private void fillDocumentReferenceMetadata(
      final DocumentMetadataResponse meta, final DocumentReferenceMetadata recordMeta) {
    recordMeta
        .setContentType(meta.getContentType() != null ? meta.getContentType() : "")
        .setFileName(meta.getFileName() != null ? meta.getFileName() : "")
        .setSize(meta.getSize() != null ? meta.getSize() : -1L);
    if (meta.getExpiresAt() != null && !meta.getExpiresAt().isBlank()) {
      recordMeta.setExpiresAt(OffsetDateTime.parse(meta.getExpiresAt()).toInstant().toEpochMilli());
    }
    if (meta.getProcessDefinitionId() != null) {
      recordMeta.setProcessDefinitionId(meta.getProcessDefinitionId());
    }
    if (meta.getProcessInstanceKey() != null && !meta.getProcessInstanceKey().isBlank()) {
      recordMeta.setProcessInstanceKey(Long.parseLong(meta.getProcessInstanceKey()));
    }
    if (meta.getCustomProperties() != null && !meta.getCustomProperties().isEmpty()) {
      recordMeta.setCustomProperties(meta.getCustomProperties());
    }
  }

  // Note: even if limits are marked @NotNull in AgentInstanceCreationRequest,
  // nothing is actually preventing them from being null
  @SuppressWarnings("ConstantValue")
  private void fillLimits(
      final AgentInstanceLimits requestLimits,
      final io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceLimits
          recordLimits) {
    if (requestLimits.getMaxTokens() != null) {
      recordLimits.setMaxTokens(requestLimits.getMaxTokens());
    }
    if (requestLimits.getMaxModelCalls() != null) {
      recordLimits.setMaxModelCalls(requestLimits.getMaxModelCalls());
    }
    if (requestLimits.getMaxToolCalls() != null) {
      recordLimits.setMaxToolCalls(requestLimits.getMaxToolCalls());
    }
  }

  private AgentInstanceStatus mapStatus(final AgentInstanceUpdateStatusEnum status) {
    return switch (status) {
      case IDLE -> AgentInstanceStatus.IDLE;
      case THINKING -> AgentInstanceStatus.THINKING;
      case TOOL_CALLING -> AgentInstanceStatus.TOOL_CALLING;
      case TOOL_DISCOVERY -> AgentInstanceStatus.TOOL_DISCOVERY;
    };
  }

  private AgentInstanceTool mapTool(final AgentTool tool) {
    final var recordTool = new AgentInstanceTool();
    recordTool.setName(tool.getName());
    if (tool.getDescription() != null) {
      recordTool.setDescription(tool.getDescription());
    }
    if (tool.getElementId() != null) {
      recordTool.setElementId(tool.getElementId());
    }
    return recordTool;
  }
}
