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
import java.util.HashSet;
import java.util.List;
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
  private static final String ERROR_MSG_DUPLICATE_ATTRIBUTES =
      "Expected to update agent instance, but changedAttributes contained duplicate attribute(s) %s.";
  private static final String ERROR_MSG_UNKNOWN_ATTRIBUTES =
      "Expected to update agent instance, but changedAttributes contained unknown attribute(s) %s. Allowed attributes are: %s.";
  private static final String ERROR_MSG_PROCESS_NOT_FOUND =
      "Expected to update agent instance with key '%d', but its deployed process (key '%d', tenant '%s') could not be resolved.";
  private static final String ERROR_MSG_NEGATIVE_METRIC =
      "Expected to update agent instance metrics, but received negative delta(s): inputTokens=%d, outputTokens=%d, modelCalls=%d, toolCalls=%d. Metric deltas must be non-negative.";
  private static final String ERROR_MSG_STATUS_UNSPECIFIED =
      "Expected to update agent instance status, but status is UNSPECIFIED.";
  private static final String ERROR_MSG_INVALID_TRANSITION =
      "Expected to update agent instance with key '%d' from status '%s' to '%s', but this transition is not allowed.";

  // Statuses that are valid as a target of an UPDATE (excluding COMPLETED, which is owned by
  // the COMPLETE command).
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
    // Use the command's key as the single source of truth — the agentInstanceKey field on the
    // record value is informational only and may diverge if a malformed command is submitted.
    final var agentInstanceKey = command.getKey();
    final var rawChangedAttributes = commandValue.getChangedAttributes();
    final Set<String> changed = Set.copyOf(rawChangedAttributes);

    // 1. Existence check — also covers the COMPLETED-then-deleted case naturally.
    final var current = agentInstanceState.getRecord(agentInstanceKey);
    if (current == null) {
      writeRejection(
          command, RejectionType.NOT_FOUND, ERROR_MSG_NOT_FOUND.formatted(agentInstanceKey));
      return;
    }

    // 2. changedAttributes must not be empty.
    if (changed.isEmpty()) {
      writeRejection(command, RejectionType.INVALID_ARGUMENT, ERROR_MSG_EMPTY_CHANGED);
      return;
    }

    // 3. Duplicate attribute names are rejected — the patch loop iterates the raw list, so a
    // duplicate metrics entry would apply the delta twice. Reject explicitly to keep the wire
    // contract clean.
    if (rawChangedAttributes.size() != changed.size()) {
      final var seen = new HashSet<String>();
      final var duplicates = new ArrayList<String>();
      for (final var attr : rawChangedAttributes) {
        if (!seen.add(attr) && !duplicates.contains(attr)) {
          duplicates.add(attr);
        }
      }
      writeRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          ERROR_MSG_DUPLICATE_ATTRIBUTES.formatted(duplicates));
      return;
    }

    // 4. Unknown attribute names are rejected.
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

    // 5. Metric deltas must be non-negative.
    if (changed.contains(ATTR_METRICS)) {
      final var metrics = commandValue.getMetrics();
      if (metrics.getInputTokens() < 0
          || metrics.getOutputTokens() < 0
          || metrics.getModelCalls() < 0
          || metrics.getToolCalls() < 0) {
        writeRejection(
            command,
            RejectionType.INVALID_ARGUMENT,
            ERROR_MSG_NEGATIVE_METRIC.formatted(
                metrics.getInputTokens(),
                metrics.getOutputTokens(),
                metrics.getModelCalls(),
                metrics.getToolCalls()));
        return;
      }
    }

    // 6. Status cannot be UNSPECIFIED when listed in changedAttributes.
    if (changed.contains(ATTR_STATUS)
        && commandValue.getStatus() == AgentInstanceStatus.UNSPECIFIED) {
      writeRejection(command, RejectionType.INVALID_ARGUMENT, ERROR_MSG_STATUS_UNSPECIFIED);
      return;
    }

    // 7. Authorization — check against the deployed process for tenant and resource id.
    // If the deployed process can no longer be resolved (e.g. it was deleted) we reject rather
    // than fall back to wildcard PROCESS_DEFINITION auth, which would silently broaden the
    // permission required of the caller.
    final var deployedProcess =
        processState.getProcessByKeyAndTenant(
            current.getProcessDefinitionKey(), current.getTenantId());
    if (deployedProcess == null) {
      writeRejection(
          command,
          RejectionType.INVALID_STATE,
          ERROR_MSG_PROCESS_NOT_FOUND.formatted(
              agentInstanceKey, current.getProcessDefinitionKey(), current.getTenantId()));
      return;
    }
    final var bpmnProcessId = BufferUtil.bufferAsString(deployedProcess.getBpmnProcessId());

    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE)
            .tenantId(current.getTenantId())
            .addResourceId(bpmnProcessId)
            .build();
    final var authResult = authCheckBehavior.isAuthorizedOrInternalCommand(authRequest);
    if (authResult.isLeft()) {
      final var rejection = authResult.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    // 8. Status transition check (only when status is changing).
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

    // All checks passed — apply the patch.
    final var originalStatus = current.getStatus();

    final var effective = new ArrayList<String>(commandValue.getChangedAttributes().size());
    for (final var attr : commandValue.getChangedAttributes()) {
      switch (attr) {
        case ATTR_STATUS -> {
          current.setStatus(commandValue.getStatus());
          // Drop "status" from the event's changedAttributes when it is a no-op.
          if (!commandValue.getStatus().equals(originalStatus)) {
            effective.add(ATTR_STATUS);
          }
        }
        case ATTR_METRICS -> {
          final var currentMetrics = current.getMetrics();
          final var deltaMetrics = commandValue.getMetrics();
          currentMetrics
              .setInputTokens(currentMetrics.getInputTokens() + deltaMetrics.getInputTokens())
              .setOutputTokens(currentMetrics.getOutputTokens() + deltaMetrics.getOutputTokens())
              .setModelCalls(currentMetrics.getModelCalls() + deltaMetrics.getModelCalls())
              .setToolCalls(currentMetrics.getToolCalls() + deltaMetrics.getToolCalls());
          effective.add(ATTR_METRICS);
        }
        case ATTR_TOOLS -> {
          current.setTools(commandValue.getTools());
          effective.add(ATTR_TOOLS);
        }
        default -> {
          // Unreachable: validated above.
        }
      }
    }

    current.setChangedAttributes(List.copyOf(effective));

    stateWriter.appendFollowUpEvent(command.getKey(), AgentInstanceIntent.UPDATED, current);
    responseWriter.writeEventOnCommand(
        command.getKey(), AgentInstanceIntent.UPDATED, current, command);
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
