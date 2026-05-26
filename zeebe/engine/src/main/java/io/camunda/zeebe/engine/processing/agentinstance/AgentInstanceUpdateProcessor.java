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
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceMetrics;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceToolValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class AgentInstanceUpdateProcessor
    implements TypedRecordProcessor<AgentInstanceRecord> {

  static final String ATTR_STATUS = "status";
  static final String ATTR_METRICS = "metrics";
  static final String ATTR_TOOLS = "tools";

  // Iteration order matters: it determines the order of names in the emitted changedAttributes
  // list and must be stable across JVMs and replays. Used both as the allow-list for incoming
  // changedAttributes and as the iteration order for applying the patch.
  private static final List<String> ALLOWED_ATTRIBUTES =
      List.of(ATTR_STATUS, ATTR_METRICS, ATTR_TOOLS);

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
  private static final String ERROR_MSG_ELEMENT_INSTANCE_KEY_MISSING =
      "Expected to update agent instance with key '%d', but elementInstanceKey is missing (got -1). The element instance key must be provided.";
  private static final String ERROR_MSG_ELEMENT_INSTANCE_NOT_FOUND =
      "Expected to update agent instance for element instance with key '%d', but no such element instance was found.";
  private static final String ERROR_MSG_ELEMENT_INSTANCE_NOT_ACTIVE =
      "Expected to update agent instance for element instance with key '%d', but it is not active.";
  private static final String ERROR_MSG_ELEMENT_ID_MISMATCH =
      "Expected to update agent instance with key '%d' for element instance with key '%d', but the element id '%s' does not match the agent instance's element id '%s'.";
  private static final String ERROR_MSG_PROCESS_INSTANCE_KEY_MISMATCH =
      "Expected to update agent instance with key '%d' for element instance with key '%d', but the process instance key '%d' does not match the agent instance's process instance key '%d'.";
  private static final String ERROR_MSG_AGENT_INSTANCE_ALREADY_EXISTS =
      "Expected to associate element instance with key '%d' with an agent instance, but it is already associated with agent instance with key '%d'.";

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
  private final ElementInstanceState elementInstanceState;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public AgentInstanceUpdateProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    agentInstanceState = processingState.getAgentInstanceState();
    elementInstanceState = processingState.getElementInstanceState();
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

    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE)
            .tenantId(current.getTenantId())
            .addResourceId(current.getBpmnProcessId())
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

    final var unknown =
        changed.stream().filter(attr -> !ALLOWED_ATTRIBUTES.contains(attr)).toList();
    if (!unknown.isEmpty()) {
      writeRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          ERROR_MSG_UNKNOWN_ATTRIBUTES.formatted(unknown, ALLOWED_ATTRIBUTES));
      return;
    }

    if (changed.contains(ATTR_METRICS) && !hasAllowedMetricDeltas(commandValue.getMetrics())) {
      final var metrics = commandValue.getMetrics();
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

    final var newElementInstanceKey = commandValue.getElementInstanceKey();
    if (newElementInstanceKey == -1L) {
      writeRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          ERROR_MSG_ELEMENT_INSTANCE_KEY_MISSING.formatted(agentInstanceKey));
      return;
    }

    final var elementInstance = elementInstanceState.getInstance(newElementInstanceKey);
    if (elementInstance == null) {
      writeRejection(
          command,
          RejectionType.NOT_FOUND,
          ERROR_MSG_ELEMENT_INSTANCE_NOT_FOUND.formatted(newElementInstanceKey));
      return;
    }

    if (!elementInstance.isActive()) {
      writeRejection(
          command,
          RejectionType.INVALID_STATE,
          ERROR_MSG_ELEMENT_INSTANCE_NOT_ACTIVE.formatted(newElementInstanceKey));
      return;
    }

    final var elementInstanceValue = elementInstance.getValue();
    if (!elementInstanceValue.getElementId().equals(current.getElementId())) {
      writeRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          ERROR_MSG_ELEMENT_ID_MISMATCH.formatted(
              agentInstanceKey,
              newElementInstanceKey,
              elementInstanceValue.getElementId(),
              current.getElementId()));
      return;
    }

    if (elementInstanceValue.getProcessInstanceKey() != current.getProcessInstanceKey()) {
      writeRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          ERROR_MSG_PROCESS_INSTANCE_KEY_MISMATCH.formatted(
              agentInstanceKey,
              newElementInstanceKey,
              elementInstanceValue.getProcessInstanceKey(),
              current.getProcessInstanceKey()));
      return;
    }

    final var existingAgentInstanceKey = elementInstance.getAgentInstanceKey();
    if (existingAgentInstanceKey != -1L && existingAgentInstanceKey != agentInstanceKey) {
      writeRejection(
          command,
          RejectionType.ALREADY_EXISTS,
          ERROR_MSG_AGENT_INSTANCE_ALREADY_EXISTS.formatted(
              newElementInstanceKey, existingAgentInstanceKey));
      return;
    }

    if (!current.getElementInstanceKeys().contains(newElementInstanceKey)) {
      current.addElementInstanceKey(newElementInstanceKey);
    }
    current.setElementInstanceKey(newElementInstanceKey);

    current.setChangedAttributes(applyPatch(current, commandValue, changed));

    stateWriter.appendFollowUpEvent(agentInstanceKey, AgentInstanceIntent.UPDATED, current);
    responseWriter.writeEventOnCommand(
        agentInstanceKey, AgentInstanceIntent.UPDATED, current, command);
  }

  /**
   * <strong>Mutates {@code current} in place.</strong> For each attribute named in {@code changed},
   * applies the corresponding value from {@code delta} to {@code current} (status/tools are
   * overwritten, metrics fields are summed). The caller's {@code current} reference observes every
   * mutation directly — nothing is copied.
   *
   * <p>Returns the effective {@code changedAttributes} for the UPDATED event — i.e. the subset of
   * {@code changed} whose values actually moved.
   */
  @SuppressWarnings("checkstyle:MissingSwitchDefault") // exhaustive over ALLOWED_ATTRIBUTES
  private static List<String> applyPatch(
      final AgentInstanceRecord current,
      final AgentInstanceRecord delta,
      final Set<String> changed) {
    final var effective = new ArrayList<String>(changed.size());
    // Iterate ALLOWED_ATTRIBUTES (not the incoming set) so the output order is fixed regardless of
    // the JVM's hash randomization or client ordering.
    for (final var attr : ALLOWED_ATTRIBUTES) {
      if (!changed.contains(attr)) {
        continue;
      }
      switch (attr) {
        case ATTR_STATUS -> {
          if (!delta.getStatus().equals(current.getStatus())) {
            effective.add(ATTR_STATUS);
          }
          current.setStatus(delta.getStatus());
        }
        case ATTR_METRICS -> {
          if (applyMetricDeltas(current.getMetrics(), delta.getMetrics())) {
            effective.add(ATTR_METRICS);
          }
        }
        case ATTR_TOOLS -> {
          if (!toolsEqual(current.getTools(), delta.getTools())) {
            current.setTools(delta.getTools());
            effective.add(ATTR_TOOLS);
          }
        }
      }
    }
    return effective;
  }

  private static boolean toolsEqual(
      final List<AgentInstanceToolValue> a, final List<AgentInstanceToolValue> b) {
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      final var x = a.get(i);
      final var y = b.get(i);
      if (!x.getName().equals(y.getName())
          || !x.getDescription().equals(y.getDescription())
          || !x.getElementId().equals(y.getElementId())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Applies each metric delta in {@code delta} to {@code current}, skipping fields whose delta is
   * not strictly positive (covers {@code -1} not-provided and {@code 0} no-change). Returns whether
   * at least one field's value moved forward.
   */
  private static boolean applyMetricDeltas(
      final AgentInstanceMetrics current, final AgentInstanceMetrics delta) {
    var moved = false;
    if (delta.getInputTokens() > 0) {
      current.setInputTokens(current.getInputTokens() + delta.getInputTokens());
      moved = true;
    }
    if (delta.getOutputTokens() > 0) {
      current.setOutputTokens(current.getOutputTokens() + delta.getOutputTokens());
      moved = true;
    }
    if (delta.getModelCalls() > 0) {
      current.setModelCalls(current.getModelCalls() + delta.getModelCalls());
      moved = true;
    }
    if (delta.getToolCalls() > 0) {
      current.setToolCalls(current.getToolCalls() + delta.getToolCalls());
      moved = true;
    }
    return moved;
  }

  private static boolean hasAllowedMetricDeltas(final AgentInstanceMetrics metrics) {
    return metrics.getInputTokens() >= METRIC_NOT_PROVIDED
        && metrics.getOutputTokens() >= METRIC_NOT_PROVIDED
        && metrics.getModelCalls() >= METRIC_NOT_PROVIDED
        && metrics.getToolCalls() >= METRIC_NOT_PROVIDED;
  }

  private boolean isAllowedTransition(
      final AgentInstanceStatus from, final AgentInstanceStatus to) {
    // Target must be one of the active states. In particular, UPDATE never moves to COMPLETED —
    // that's owned by the COMPLETE command.
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
