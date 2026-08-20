/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The primary suspension gate: decides how a command should be treated while its target process
 * instance carries a suspension marker.
 *
 * <p>Only processors that implement {@link SuspensionAware} are gated; every other command is
 * processed normally. An implementing processor's {@link SuspensionAware#suspensionBehavior}
 * classifies the command as {@code PROCESS}, {@code REJECT}, or {@code BUFFER}.
 */
@NullMarked
public final class SuspensionCheck {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private final ProcessingState processingState;

  public SuspensionCheck(final ProcessingState processingState) {
    this.processingState = processingState;
  }

  /**
   * Decides how to treat the command; commands whose processor is not {@link SuspensionAware}, or
   * whose target is not suspended, are always processed. The resolved target process instance key
   * is returned alongside the decision so callers reuse it rather than re-deriving it (external
   * {@code JOB}/{@code INCIDENT}/{@code USER_TASK}/{@code AD_HOC_SUB_PROCESS_INSTRUCTION} commands
   * don't carry it on the wire).
   */
  public SuspensionResult resolve(
      final TypedRecord<?> command, final TypedRecordProcessor<?> processor) {
    if (!(processor instanceof final SuspensionAware<?> suspensionAware)) {
      // processors that don't opt in via SuspensionAware are never gated; checked before resolving
      // the process instance key to keep the state lookups off the hot path for unrelated commands
      return new SuspensionResult(SuspensionBehavior.PROCESS, -1);
    }

    final long processInstanceKey = resolveProcessInstanceKey(command);
    if (processInstanceKey <= 0) {
      return new SuspensionResult(SuspensionBehavior.PROCESS, processInstanceKey);
    }

    final State marker =
        processingState.getSuspensionState().getSuspensionState(processInstanceKey);
    if (marker == null) {
      return new SuspensionResult(SuspensionBehavior.PROCESS, processInstanceKey);
    }

    final SuspensionBehavior behavior = suspensionBehavior(suspensionAware, command);
    if (behavior == null) {
      LOG.error(
          "Processor '{}' implements SuspensionAware but returned a null suspension behavior for"
              + " command '{}'; processing it normally. Please report this as a bug.",
          processor.getClass().getName(),
          command.getValueType());
      return new SuspensionResult(SuspensionBehavior.PROCESS, processInstanceKey);
    }

    final SuspensionBehavior decision =
        switch (behavior) {
          case PROCESS -> SuspensionBehavior.PROCESS;
          case REJECT -> SuspensionBehavior.REJECT;
          case BUFFER ->
              marker == State.SUSPENDED
                  ? SuspensionBehavior.BUFFER
                  // RESUMING: pass through so drained commands can execute.
                  : SuspensionBehavior.PROCESS;
        };
    return new SuspensionResult(decision, processInstanceKey);
  }

  /**
   * Resolves the process instance a command targets. Most values carry their own {@code
   * processInstanceKey}. However, a few other external commands only carry the entity key, so the
   * persisted entity is consulted. Returns {@code -1} when it can't be resolved.
   */
  private long resolveProcessInstanceKey(final TypedRecord<?> command) {
    if (command.getValue() instanceof final ProcessInstanceRelated processInstanceRelated) {
      final long processInstanceKey = processInstanceRelated.getProcessInstanceKey();
      if (processInstanceKey > 0) {
        return processInstanceKey;
      }
    }

    final long key = command.getKey();
    return switch (command.getValueType()) {
      case JOB -> {
        final var job = processingState.getJobState().getJob(key);
        yield job != null ? job.getProcessInstanceKey() : -1;
      }
      case INCIDENT -> {
        final var incident = processingState.getIncidentState().getIncidentRecord(key);
        yield incident != null ? incident.getProcessInstanceKey() : -1;
      }
      case USER_TASK -> {
        final var userTask = processingState.getUserTaskState().getUserTask(key);
        yield userTask != null ? userTask.getProcessInstanceKey() : -1;
      }
      case AD_HOC_SUB_PROCESS_INSTRUCTION -> {
        final var adHocValue = (AdHocSubProcessInstructionRecordValue) command.getValue();
        final var elementInstance =
            processingState
                .getElementInstanceState()
                .getInstance(adHocValue.getAdHocSubProcessInstanceKey());
        yield elementInstance != null ? elementInstance.getValue().getProcessInstanceKey() : -1;
      }
      case VARIABLE_DOCUMENT -> {
        final var scopeKey = ((VariableDocumentRecordValue) command.getValue()).getScopeKey();
        final var scope = processingState.getElementInstanceState().getInstance(scopeKey);
        yield scope != null ? scope.getValue().getProcessInstanceKey() : -1;
      }
      case AGENT_INSTANCE -> resolveAgentInstanceProcessInstanceKey(command);
      case AGENT_HISTORY -> resolveAgentHistoryProcessInstanceKey(command);
      default -> -1;
    };
  }

  /**
   * CREATE carries {@code elementInstanceKey} on the value; the target element instance's process
   * instance key is looked up. Every other AgentInstance command (currently only UPDATE) targets an
   * existing agent instance identified by the command's own key.
   */
  private long resolveAgentInstanceProcessInstanceKey(final TypedRecord<?> command) {
    if (command.getIntent() == AgentInstanceIntent.CREATE) {
      final var value = (AgentInstanceRecordValue) command.getValue();
      final var elementInstance =
          processingState.getElementInstanceState().getInstance(value.getElementInstanceKey());
      return elementInstance != null ? elementInstance.getValue().getProcessInstanceKey() : -1;
    }

    final var agentInstance = processingState.getAgentInstanceState().getRecord(command.getKey());
    return agentInstance != null ? agentInstance.getProcessInstanceKey() : -1;
  }

  /**
   * CREATE carries {@code agentInstanceKey} on the value; the target agent instance's process
   * instance key is looked up.
   */
  private long resolveAgentHistoryProcessInstanceKey(final TypedRecord<?> command) {
    final var value = (AgentHistoryRecordValue) command.getValue();
    final var agentInstance =
        processingState.getAgentInstanceState().getRecord(value.getAgentInstanceKey());
    return agentInstance != null ? agentInstance.getProcessInstanceKey() : -1;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static @Nullable SuspensionBehavior suspensionBehavior(
      final SuspensionAware<?> suspensionAware, final TypedRecord<?> command) {
    return ((SuspensionAware) suspensionAware).suspensionBehavior(command);
  }

  /** The gate outcome for a command, with the resolved target process instance key. */
  public record SuspensionResult(SuspensionBehavior outcome, long processInstanceKey) {}
}
