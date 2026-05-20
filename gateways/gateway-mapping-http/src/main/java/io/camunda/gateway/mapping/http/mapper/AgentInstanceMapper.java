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
import io.camunda.gateway.protocol.model.AgentInstanceLimits;
import io.camunda.gateway.protocol.model.AgentInstanceStatusEnum;
import io.camunda.gateway.protocol.model.AgentInstanceUpdateRequest;
import io.camunda.gateway.protocol.model.AgentTool;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceTool;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.stream.Collectors;
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

  private AgentInstanceStatus mapStatus(final AgentInstanceStatusEnum status) {
    return switch (status) {
      case COMPLETED -> AgentInstanceStatus.COMPLETED;
      case IDLE -> AgentInstanceStatus.IDLE;
      case INITIALIZING -> AgentInstanceStatus.INITIALIZING;
      case THINKING -> AgentInstanceStatus.THINKING;
      case TOOL_CALLING -> AgentInstanceStatus.TOOL_CALLING;
      case TOOL_DISCOVERY -> AgentInstanceStatus.TOOL_DISCOVERY;
        // No need for default: if a new possible value is added to the API spec
        // then this statement will fail to compile
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
