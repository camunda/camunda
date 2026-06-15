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
import io.camunda.gateway.protocol.model.AgentInstanceCreationRequest;
import io.camunda.gateway.protocol.model.AgentInstanceCreationResult;
import io.camunda.gateway.protocol.model.AgentInstanceDocumentContent;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryItemCreationResult;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryItemRequest;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryRoleEnum;
import io.camunda.gateway.protocol.model.AgentInstanceLimits;
import io.camunda.gateway.protocol.model.AgentInstanceMessageContent;
import io.camunda.gateway.protocol.model.AgentInstanceObjectContent;
import io.camunda.gateway.protocol.model.AgentInstanceTextContent;
import io.camunda.gateway.protocol.model.AgentInstanceToolCall;
import io.camunda.gateway.protocol.model.AgentInstanceUpdateRequest;
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
import java.util.Map;
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

          final var def = request.getDefinition();
          record
              .getDefinition()
              .setModel(def.getModel())
              .setProvider(def.getProvider())
              .setSystemPrompt(def.getSystemPrompt());

          if (request.getLimits() != null) {
            fillLimits(request.getLimits(), record.getLimits());
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

          if (request.getMetrics() != null) {
            final var delta = request.getMetrics();
            if (delta.getInputTokens() != null) {
              record.getMetrics().setInputTokens(delta.getInputTokens());
            }
            if (delta.getOutputTokens() != null) {
              record.getMetrics().setOutputTokens(delta.getOutputTokens());
            }
            if (delta.getModelCalls() != null) {
              record.getMetrics().setModelCalls(delta.getModelCalls());
            }
            if (delta.getToolCalls() != null) {
              record.getMetrics().setToolCalls(delta.getToolCalls());
            }
            record.addChangedAttribute("metrics");
          }

          if (request.getTools() != null) {
            final List<AgentInstanceTool> tools =
                request.getTools().stream().map(this::mapTool).collect(Collectors.toList());
            record.setTools(tools);
            record.addChangedAttribute("tools");
          }

          return record;
        });
  }

  public AgentInstanceCreationResult toAgentInstanceCreationResult(
      final AgentInstanceRecord record) {
    return AgentInstanceCreationResult.Builder.create()
        .agentInstanceKey(KeyUtil.keyToString(record.getAgentInstanceKey()))
        .build();
  }

  public Either<ProblemDetail, AgentHistoryRecord> toCreateAgentHistoryRecord(
      final String agentInstanceKey, final AgentInstanceHistoryItemRequest request) {
    return RequestMapper.getResult(
        requestValidator.validateHistoryItemRequest(agentInstanceKey, request),
        () -> {
          final var record = new AgentHistoryRecord();

          record.setAgentInstanceKey(Long.parseLong(agentInstanceKey));
          record.setElementInstanceKey(Long.parseLong(request.getElementInstanceKey()));
          record.setJobKey(Long.parseLong(request.getJobKey()));
          record.setJobLease(request.getJobLease());
          record.setRole(mapHistoryRole(request.getRole()));
          record.setProducedAt(
              OffsetDateTime.parse(request.getProducedAt()).toInstant().toEpochMilli());

          if (request.getIteration() != null) {
            record.setIteration(request.getIteration());
          }

          for (final AgentInstanceMessageContent content : request.getContent()) {
            record.addContent(mapContent(content));
          }

          if (request.getToolCalls() != null) {
            for (final AgentInstanceToolCall toolCall : request.getToolCalls()) {
              record.addToolCall(mapToolCall(toolCall));
            }
          }

          if (request.getMetrics() != null) {
            final var metrics = request.getMetrics();
            record
                .getMetrics()
                .setInputTokens(metrics.getInputTokens() != null ? metrics.getInputTokens() : 0L)
                .setOutputTokens(metrics.getOutputTokens() != null ? metrics.getOutputTokens() : 0L)
                .setDurationMs(metrics.getDurationMs() != null ? metrics.getDurationMs() : 0L);
          }

          return record;
        });
  }

  public AgentInstanceHistoryItemCreationResult toAgentHistoryItemCreationResult(
      final AgentHistoryRecord record) {
    return AgentInstanceHistoryItemCreationResult.Builder.create()
        .historyItemKey(KeyUtil.keyToString(record.getAgentHistoryKey()))
        .build();
  }

  private AgentHistoryRole mapHistoryRole(final AgentInstanceHistoryRoleEnum role) {
    return switch (role) {
      case USER -> AgentHistoryRole.USER;
      case ASSISTANT -> AgentHistoryRole.ASSISTANT;
      case TOOL_RESULT -> AgentHistoryRole.TOOL_RESULT;
    };
  }

  private AgentHistoryMessageContent mapContent(final AgentInstanceMessageContent content) {
    final var result = new AgentHistoryMessageContent();
    if (content instanceof final AgentInstanceTextContent text) {
      result.setContentType(AgentHistoryContentType.TEXT).setText(text.getText());
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
    result.setToolCallId(toolCall.getToolCallId());
    result.setToolName(toolCall.getToolName());
    if (toolCall.getElementId() != null) {
      result.setElementId(toolCall.getElementId());
    }
    if (toolCall.getArguments() != null) {
      result.setArguments(toMsgPackBuffer(toolCall.getArguments()));
    }
    return result;
  }

  private DirectBuffer toMsgPackBuffer(final Map<String, Object> map) {
    return BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(map));
  }

  private void fillDocumentReferenceMetadata(
      final DocumentMetadataResponse meta, final DocumentReferenceMetadata recordMeta) {
    recordMeta
        .setContentType(meta.getContentType() != null ? meta.getContentType() : "")
        .setFileName(meta.getFileName() != null ? meta.getFileName() : "")
        .setSize(meta.getSize() != null ? meta.getSize() : -1L);
    if (meta.getExpiresAt() != null) {
      recordMeta.setExpiresAt(OffsetDateTime.parse(meta.getExpiresAt()).toInstant().toEpochMilli());
    }
    if (meta.getProcessDefinitionId() != null) {
      recordMeta.setProcessDefinitionId(meta.getProcessDefinitionId());
    }
    if (meta.getProcessInstanceKey() != null) {
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
