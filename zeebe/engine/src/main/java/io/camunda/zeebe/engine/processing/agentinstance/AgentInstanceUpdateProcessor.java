/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AgentInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

public final class AgentInstanceUpdateProcessor
    implements TypedRecordProcessor<AgentInstanceRecord> {

  static final String ATTR_STATUS = "status";
  static final String ATTR_METRICS = "metrics";
  static final String ATTR_TOOLS = "tools";

  private static final Set<String> ALLOWED_ATTRIBUTES =
      Set.of(ATTR_STATUS, ATTR_METRICS, ATTR_TOOLS);

  private static final String ERROR_MSG_NOT_FOUND =
      "Expected to update agent instance with key '%d', but no such agent instance was found.";
  private static final String ERROR_MSG_EMPTY_CHANGED =
      "Expected to update agent instance, but changedAttributes is empty.";
  private static final String ERROR_MSG_UNKNOWN_ATTRIBUTES =
      "Expected to update agent instance, but changedAttributes contained unknown attribute(s) %s. Allowed attributes are: %s.";
  private static final String ERROR_MSG_INVALID_METRIC_DELTA =
      "Expected to update agent instance metrics, but received invalid delta(s): inputTokens=%d, outputTokens=%d, modelCalls=%d, toolCalls=%d. Each metric delta must be either -1 (field not provided) or a non-negative value.";
  private static final String ERROR_MSG_INVALID_TRANSITION =
      "Expected to update agent instance with key '%d' from status '%s' to '%s', but this transition is not allowed.";

  // -1 signals that the gateway did not receive a value for the field — treat as not-provided.
  // 0 means the field was provided but did not move; both are no-ops in the engine.
  private static final long METRIC_NOT_PROVIDED = -1L;

  private static final Set<AgentInstanceStatus> ACTIVE_STATUSES =
      EnumSet.of(
          AgentInstanceStatus.INITIALIZING,
          AgentInstanceStatus.TOOL_DISCOVERY,
          AgentInstanceStatus.THINKING,
          AgentInstanceStatus.TOOL_CALLING,
          AgentInstanceStatus.IDLE);

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final AgentInstanceState agentInstanceState;
  private final ProcessState processState;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public AgentInstanceUpdateProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    agentInstanceState = processingState.getAgentInstanceState();
    processState = processingState.getProcessState();
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<AgentInstanceRecord> command) {
    final var commandValue = command.getValue();
    final var agentInstanceKey = command.getKey();

    final var current = agentInstanceState.getRecord(agentInstanceKey);
    if (current == null) {
      writeRejection(
          command, RejectionType.NOT_FOUND, ERROR_MSG_NOT_FOUND.formatted(agentInstanceKey));
      return;
    }

    final var deployedProcess =
        processState.getProcessByKeyAndTenant(
            current.getProcessDefinitionKey(), current.getTenantId());
    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE)
            .tenantId(current.getTenantId())
            .addResourceId(BufferUtil.bufferAsString(deployedProcess.getBpmnProcessId()))
            .build();
    final var authResult = authCheckBehavior.isAuthorizedOrInternalCommand(authRequest);
    if (authResult.isLeft()) {
      final var rejection = authResult.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    final Set<String> changed = Set.copyOf(commandValue.getChangedAttributes());
    if (changed.isEmpty()) {
      writeRejection(command, RejectionType.INVALID_ARGUMENT, ERROR_MSG_EMPTY_CHANGED);
      return;
    }

    final var unknown = new ArrayList<String>();
    for (final var attr : changed) {
      if (!ALLOWED_ATTRIBUTES.contains(attr)) {
        unknown.add(attr);
      }
    }
    if (!unknown.isEmpty()) {
      writeRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          ERROR_MSG_UNKNOWN_ATTRIBUTES.formatted(unknown, ALLOWED_ATTRIBUTES));
      return;
    }

    if (changed.contains(ATTR_METRICS)) {
      final var metrics = commandValue.getMetrics();
      if (isInvalidMetricDelta(metrics.getInputTokens())
          || isInvalidMetricDelta(metrics.getOutputTokens())
          || isInvalidMetricDelta(metrics.getModelCalls())
          || isInvalidMetricDelta(metrics.getToolCalls())) {
        writeRejection(
            command,
            RejectionType.INVALID_ARGUMENT,
            ERROR_MSG_INVALID_METRIC_DELTA.formatted(
                metrics.getInputTokens(),
                metrics.getOutputTokens(),
                metrics.getModelCalls(),
                metrics.getToolCalls()));
        return;
      }
    }

    if (changed.contains(ATTR_STATUS)) {
      final var from = current.getStatus();
      final var to = commandValue.getStatus();
      if (!isAllowedTransition(from, to)) {
        writeRejection(
            command,
            RejectionType.INVALID_STATE,
            ERROR_MSG_INVALID_TRANSITION.formatted(agentInstanceKey, from, to));
        return;
      }
    }

    final var effective = new ArrayList<String>(changed.size());
    for (final var attr : changed) {
      switch (attr) {
        case ATTR_STATUS -> {
          // Drop "status" from the event's changedAttributes when it is a no-op.
          if (!commandValue.getStatus().equals(current.getStatus())) {
            effective.add(ATTR_STATUS);
          }
          current.setStatus(commandValue.getStatus());
        }
        case ATTR_METRICS -> {
          // Apply each delta, skipping -1 (not provided) and 0 (provided but unchanged). Only
          // include "metrics" in the event's changedAttributes when at least one field actually
          // moved forward.
          final var currentMetrics = current.getMetrics();
          final var deltaMetrics = commandValue.getMetrics();
          var metricsChanged = false;
          if (deltaMetrics.getInputTokens() > 0) {
            currentMetrics.setInputTokens(
                currentMetrics.getInputTokens() + deltaMetrics.getInputTokens());
            metricsChanged = true;
          }
          if (deltaMetrics.getOutputTokens() > 0) {
            currentMetrics.setOutputTokens(
                currentMetrics.getOutputTokens() + deltaMetrics.getOutputTokens());
            metricsChanged = true;
          }
          if (deltaMetrics.getModelCalls() > 0) {
            currentMetrics.setModelCalls(
                currentMetrics.getModelCalls() + deltaMetrics.getModelCalls());
            metricsChanged = true;
          }
          if (deltaMetrics.getToolCalls() > 0) {
            currentMetrics.setToolCalls(
                currentMetrics.getToolCalls() + deltaMetrics.getToolCalls());
            metricsChanged = true;
          }
          if (metricsChanged) {
            effective.add(ATTR_METRICS);
          }
        }
        case ATTR_TOOLS -> {
          current.setTools(commandValue.getTools());
          effective.add(ATTR_TOOLS);
        }
      }
    }

    current.setChangedAttributes(effective);

    stateWriter.appendFollowUpEvent(agentInstanceKey, AgentInstanceIntent.UPDATED, current);
    responseWriter.writeEventOnCommand(
        agentInstanceKey, AgentInstanceIntent.UPDATED, current, command);
  }

  private static boolean isInvalidMetricDelta(final long delta) {
    return delta < METRIC_NOT_PROVIDED;
  }

  private static boolean isInvalidMetricDelta(final int delta) {
    return delta < METRIC_NOT_PROVIDED;
  }

  private boolean isAllowedTransition(
      final AgentInstanceStatus from, final AgentInstanceStatus to) {
    // UPDATE never moves to COMPLETED — that's owned by the COMPLETE command.
    if (to == AgentInstanceStatus.COMPLETED) {
      return false;
    }
    // Target must be one of the active states.
    if (!ACTIVE_STATUSES.contains(to)) {
      return false;
    }
    // From any non-INITIALIZING active state, going back to INITIALIZING is not allowed.
    if (to == AgentInstanceStatus.INITIALIZING && from != AgentInstanceStatus.INITIALIZING) {
      return false;
    }
    return true;
  }

  private void writeRejection(
      final TypedRecord<AgentInstanceRecord> command,
      final RejectionType rejectionType,
      final String reason) {
    rejectionWriter.appendRejection(command, rejectionType, reason);
    responseWriter.writeRejectionOnCommand(command, rejectionType, reason);
  }
}
