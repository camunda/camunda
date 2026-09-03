/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.agentinstance.AgentHistoryBatchBehavior.LeaseMismatchHandling;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AgentHistoryState;
import io.camunda.zeebe.engine.state.immutable.AgentInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceMetrics;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceToolValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class AgentInstanceUpdateProcessor
    implements TypedRecordProcessor<AgentInstanceRecord>, SuspensionAware<AgentInstanceRecord> {

  private static final Set<String> ALLOWED_REQUEST_LEVEL_ATTRIBUTES =
      Set.of(AgentInstanceRecord.ATTR_STATUS);
  private static final Set<String> BACKWARDS_COMPAT_ATTRIBUTES =
      Set.of(
          AgentInstanceRecord.ATTR_STATUS,
          AgentInstanceRecord.ATTR_METRICS,
          AgentInstanceRecord.ATTR_TOOLS);

  private static final String ERROR_MSG_NOT_FOUND =
      "Expected to update agent instance with key '%d', but no such agent instance was found.";
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
  private static final String ERROR_MSG_SECOND_ACTIVE_WRITER =
      "Expected to update agent instance with key '%d' for element instance with key '%d', but "
          + "element instance with key '%d' is still the active writer for this agent instance "
          + "and has not completed. Only one element instance may write to a given agent instance "
          + "at a time.";
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
  private final JobState jobState;
  private final AgentHistoryState agentHistoryState;
  private final CslAuthorizationCheck cslCheck;
  private final AgentHistoryBatchBehavior historyBatchHelper;

  public AgentInstanceUpdateProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final CslAuthorizationCheck cslCheck,
      final KeyGenerator keyGenerator) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    agentInstanceState = processingState.getAgentInstanceState();
    elementInstanceState = processingState.getElementInstanceState();
    jobState = processingState.getJobState();
    agentHistoryState = processingState.getAgentHistoryState();
    this.cslCheck = cslCheck;
    historyBatchHelper = new AgentHistoryBatchBehavior(keyGenerator, processingState);
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

    final var isAuthorized =
        cslCheck.checkAuthorizationAndTenant(
            command,
            RequiredAuthorization.of(
                b ->
                    b.resourceType(
                            AuthzModelMapper.fromProtocol(
                                AuthorizationResourceType.PROCESS_DEFINITION))
                        .permissionType(
                            AuthzModelMapper.fromProtocol(PermissionType.UPDATE_PROCESS_INSTANCE))
                        .resourceId(current.getBpmnProcessId())),
            command.getValue(),
            AuthorizationRejectionMapper.forbidden(
                PermissionType.UPDATE_PROCESS_INSTANCE,
                AuthorizationResourceType.PROCESS_DEFINITION),
            current.getTenantId(),
            new Rejection(
                RejectionType.NOT_FOUND, ERROR_MSG_NOT_FOUND.formatted(agentInstanceKey)));
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
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

    final var validWriter = validateSingleActiveWriter(current, newElementInstanceKey);
    if (validWriter.isLeft()) {
      final var rejection = validWriter.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    final var validJob =
        historyBatchHelper.validateJobContext(
            commandValue.getJobKey(),
            commandValue.getJobLease(),
            commandValue.getElementInstanceKey(),
            commandValue.getHistory(),
            LeaseMismatchHandling.ALLOW_STALE);
    if (validJob.isLeft()) {
      final var rejection = validJob.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    final var isHistoryValid = historyBatchHelper.validateHistory(commandValue.getHistory());
    if (isHistoryValid.isLeft()) {
      final var rejection = isHistoryValid.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    final var validPatch = validateRequestLevelChanges(command, current);
    if (validPatch.isLeft()) {
      final var rejection = validPatch.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    if (!current.getElementInstanceKeys().contains(newElementInstanceKey)) {
      current.addElementInstanceKey(newElementInstanceKey);
    }
    current.setElementInstanceKey(newElementInstanceKey);

    final var changedAttributes = new HashSet<String>();

    if (!commandValue.getHistory().isEmpty()) {
      final boolean jobRecordExists = validJob.get() != null;
      final var historyChanges =
          historyBatchHelper.applyInstanceChangesFromHistory(
              current,
              commandValue.getJobKey(),
              commandValue.getJobLease(),
              commandValue.getElementInstanceKey(),
              commandValue.getHistory());

      current.getHistory().stream()
          .filter(Predicate.not(AgentHistoryRecordValue::isDuplicate))
          .forEach(
              item -> {
                stateWriter.appendFollowUpEvent(
                    item.getAgentHistoryKey(), AgentHistoryIntent.CREATED, item);
                if (!jobRecordExists) {
                  // Re-read the item from state, trimmed to identity fields by
                  // AgentHistoryCreatedApplier, instead of reusing the in-memory item, so this
                  // DISCARDED event carries the same trimmed shape as every other discard path
                  // (AgentHistoryDiscardProcessor, AgentHistoryCommitProcessor).
                  final var storedItem = agentHistoryState.get(item.getAgentHistoryKey());
                  stateWriter.appendFollowUpEvent(
                      item.getAgentHistoryKey(), AgentHistoryIntent.DISCARDED, storedItem);
                }
              });

      changedAttributes.addAll(historyChanges);
    }

    final var requestLevelChanges =
        applyRequestLevelChanges(current, commandValue, validPatch.get());
    changedAttributes.addAll(requestLevelChanges);

    current.setChangedAttributes(changedAttributes.stream().sorted().toList());

    stateWriter.appendFollowUpEvent(agentInstanceKey, AgentInstanceIntent.UPDATED, current);
    responseWriter.writeAcceptedResponseOnCommand(
        agentInstanceKey, AgentInstanceIntent.UPDATED, current, command);
  }

  /**
   * Rejects the update if a different element instance already holds the write claim for this agent
   * instance and its job is still active.
   */
  private Either<Rejection, Void> validateSingleActiveWriter(
      final AgentInstanceRecord current, final long newElementInstanceKey) {
    final var activeWriter = current.getElementInstanceKey();
    if (activeWriter == -1L || activeWriter == newElementInstanceKey) {
      return Either.right(null);
    }

    // A writer counts as active only while its job is ACTIVATED, not while its element instance is
    // active: history resolves in the same step as the job, but the element instance can stay
    // active longer for unrelated reasons (e.g. Ad-Hoc Sub-Process).
    final var writer = elementInstanceState.getInstance(activeWriter);
    final var writerJobKey = writer == null ? -1L : writer.getJobKey();
    if (writerJobKey == -1L || jobState.getState(writerJobKey) != JobState.State.ACTIVATED) {
      return Either.right(null);
    }

    return Either.left(
        new Rejection(
            RejectionType.INVALID_STATE,
            ERROR_MSG_SECOND_ACTIVE_WRITER.formatted(
                current.getAgentInstanceKey(), newElementInstanceKey, activeWriter)));
  }

  private Either<Rejection, List<String>> validateRequestLevelChanges(
      final TypedRecord<AgentInstanceRecord> command, final AgentInstanceRecord current) {

    final var commandValue = command.getValue();
    final var agentInstanceKey = current.getAgentInstanceKey();
    final Set<String> changed = Set.copyOf(commandValue.getChangedAttributes());

    final var allowedAttributes =
        commandValue.getHistory().isEmpty()
            ? BACKWARDS_COMPAT_ATTRIBUTES
            : ALLOWED_REQUEST_LEVEL_ATTRIBUTES;

    final var unknown = changed.stream().filter(attr -> !allowedAttributes.contains(attr)).toList();
    if (!unknown.isEmpty()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              ERROR_MSG_UNKNOWN_ATTRIBUTES.formatted(
                  unknown, allowedAttributes.stream().sorted().toList())));
    }

    if (changed.contains(AgentInstanceRecord.ATTR_METRICS)
        && !hasAllowedMetricDeltas(commandValue.getMetrics())) {
      final var metrics = commandValue.getMetrics();
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              ERROR_MSG_INVALID_METRIC_DELTA.formatted(
                  metrics.getInputTokens(),
                  metrics.getOutputTokens(),
                  metrics.getModelCalls(),
                  metrics.getToolCalls())));
    }

    if (changed.contains(AgentInstanceRecord.ATTR_STATUS)) {
      final var from = current.getStatus();
      final var to = commandValue.getStatus();
      if (!isAllowedTransition(from, to)) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_STATE,
                ERROR_MSG_INVALID_TRANSITION.formatted(agentInstanceKey, from, to)));
      }
    }

    return Either.right(new ArrayList<>(changed));
  }

  private List<String> applyRequestLevelChanges(
      final AgentInstanceRecord current,
      final AgentInstanceRecord delta,
      final List<String> changed) {

    final var allowedAttributes =
        delta.getHistory().isEmpty()
            ? BACKWARDS_COMPAT_ATTRIBUTES
            : ALLOWED_REQUEST_LEVEL_ATTRIBUTES;

    final var effective = new ArrayList<String>(changed.size());
    for (final var attr : allowedAttributes) {
      if (!changed.contains(attr)) {
        continue;
      }
      switch (attr) {
        case AgentInstanceRecord.ATTR_STATUS -> {
          if (!delta.getStatus().equals(current.getStatus())) {
            effective.add(AgentInstanceRecord.ATTR_STATUS);
          }
          current.setStatus(delta.getStatus());
        }
        case AgentInstanceRecord.ATTR_METRICS -> {
          if (applyMetricDeltas(current.getMetrics(), delta.getMetrics())) {
            effective.add(AgentInstanceRecord.ATTR_METRICS);
          }
        }
        case AgentInstanceRecord.ATTR_TOOLS -> {
          if (!toolsEqual(current.getTools(), delta.getTools())) {
            current.setTools(delta.getTools());
            effective.add(AgentInstanceRecord.ATTR_TOOLS);
          }
        }
        default -> {
          // allowedAttributes only ever contains the three cases above; nothing else to apply.
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
    responseWriter.writeRejectedResponseOnCommand(command, rejectionType, reason);
  }

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<AgentInstanceRecord> record) {
    return record.isInternalCommand() ? SuspensionBehavior.BUFFER : SuspensionBehavior.REJECT;
  }
}
