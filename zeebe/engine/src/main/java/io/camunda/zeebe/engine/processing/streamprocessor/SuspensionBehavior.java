/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionAction;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
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
 * processed normally. An implementing processor's {@link SuspensionAware#onSuspended} classifies
 * the command as {@code PROCESS}, {@code REJECT}, or {@code BUFFER} while {@code SUSPENDED}. While
 * {@code RESUMING}, {@link SuspensionAware#onResuming} classifies instead.
 */
@NullMarked
public final class SuspensionBehavior {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private static final InstanceKeys UNRESOLVED_INSTANCE_KEYS = new InstanceKeys(-1, -1);

  private final ProcessingState processingState;

  public SuspensionBehavior(final ProcessingState processingState) {
    this.processingState = processingState;
  }

  /**
   * Decides how to treat the command; commands whose processor is not {@link SuspensionAware}, or
   * whose target is not suspended, are always processed. The resolved target process instance key
   * is returned alongside the decision so callers reuse it rather than re-deriving it (external
   * {@code JOB}/{@code INCIDENT}/{@code USER_TASK}/{@code AD_HOC_SUB_PROCESS_INSTRUCTION} commands
   * don't carry it on the wire).
   */
  public SuspensionResult process(
      final TypedRecord<?> command, final TypedRecordProcessor<?> processor) {
    if (!(processor instanceof final SuspensionAware<?> suspensionAware)) {
      // processors that don't opt in via SuspensionAware are never gated; checked before resolving
      // the process instance key to keep the state lookups off the hot path for unrelated commands
      return passThrough(-1);
    }

    final InstanceKeys keys = resolveInstanceKeys(command);
    final long processInstanceKey = keys.processInstanceKey();
    if (processInstanceKey <= 0) {
      return passThrough(processInstanceKey);
    }

    final State marker =
        processingState.getSuspensionState().getSuspensionState(processInstanceKey);

    if (marker != null && isTerminating(keys.elementInstanceKey())) {
      return passThrough(processInstanceKey);
    }

    final SuspensionAction action =
        switch (marker) {
          case SUSPENDED -> onSuspended(suspensionAware, command);
          case RESUMING -> onResuming(suspensionAware, command);
          case null -> SuspensionAction.PROCESS;
        };

    // captures onSuspended and onResuming null return values
    if (action == null) {
      LOG.error(
          "Processor '{}' implements SuspensionAware but returned a null suspension behavior for"
              + " command '{}'; processing it normally. Please report this as a bug.",
          processor.getClass().getName(),
          command.getValueType());
      return passThrough(processInstanceKey);
    }

    return new SuspensionResult(
        action,
        processInstanceKey,
        rejectionReasonFor(action, suspensionAware, command, processInstanceKey));
  }

  private static SuspensionResult passThrough(final long processInstanceKey) {
    return new SuspensionResult(SuspensionAction.PROCESS, processInstanceKey, null);
  }

  /** Resolves the instance keys from the command value, falling back to state; -1 if unknown. */
  private InstanceKeys resolveInstanceKeys(final TypedRecord<?> command) {
    final var value = command.getValue();
    if (value instanceof final ProcessInstanceRelated processInstanceRelated
        && processInstanceRelated.getProcessInstanceKey() > 0) {
      return instanceKeysOf(command, processInstanceRelated);
    }

    final long key = command.getKey();
    return switch (command.getValueType()) {
      case JOB -> instanceKeysOf(command, processingState.getJobState().getJob(key));
      case INCIDENT ->
          instanceKeysOf(command, processingState.getIncidentState().getIncidentRecord(key));
      case USER_TASK ->
          instanceKeysOf(command, processingState.getUserTaskState().getUserTask(key));
      case AD_HOC_SUB_PROCESS_INSTRUCTION -> {
        final var adHocValue = (AdHocSubProcessInstructionRecordValue) value;
        final var elementInstance =
            processingState
                .getElementInstanceState()
                .getInstance(adHocValue.getAdHocSubProcessInstanceKey());
        if (elementInstance == null) {
          yield UNRESOLVED_INSTANCE_KEYS;
        }
        yield processInstanceOnly(elementInstance.getValue().getProcessInstanceKey());
      }
      case VARIABLE_DOCUMENT -> {
        final var scopeKey = ((VariableDocumentRecordValue) value).getScopeKey();
        final var scope = processingState.getElementInstanceState().getInstance(scopeKey);
        if (scope == null) {
          yield UNRESOLVED_INSTANCE_KEYS;
        }
        yield processInstanceOnly(scope.getValue().getProcessInstanceKey());
      }
      case AGENT_INSTANCE -> processInstanceOnly(resolveAgentInstanceProcessInstanceKey(command));
      default -> UNRESOLVED_INSTANCE_KEYS;
    };
  }

  private static InstanceKeys instanceKeysOf(
      final TypedRecord<?> command, final @Nullable ProcessInstanceRelated entity) {
    if (entity == null) {
      return UNRESOLVED_INSTANCE_KEYS;
    }
    final long elementInstanceKey =
        switch (entity) {
          case final JobRecordValue job -> job.getElementInstanceKey();
          case final UserTaskRecordValue userTask -> userTask.getElementInstanceKey();
          case final IncidentRecordValue incident -> incident.getElementInstanceKey();
          // keyed by the element instance whose listener completed, e.g. a cancel listener
          case final ProcessInstanceRecordValue ignored
              when command.getIntent() == ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER ->
              command.getKey();
          default -> -1;
        };
    return new InstanceKeys(entity.getProcessInstanceKey(), elementInstanceKey);
  }

  private static InstanceKeys processInstanceOnly(final long processInstanceKey) {
    return new InstanceKeys(processInstanceKey, -1);
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
   * Commands on a terminating element (e.g. a canceling task listener job) are work the termination
   * waits for, so they must not be blocked.
   */
  private boolean isTerminating(final long elementInstanceKey) {
    if (elementInstanceKey <= 0) {
      return false;
    }
    final var elementInstance =
        processingState.getElementInstanceState().getInstance(elementInstanceKey);
    return elementInstance != null && elementInstance.isTerminating();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static SuspensionAware.@Nullable SuspensionAction onSuspended(
      final SuspensionAware<?> suspensionAware, final TypedRecord<?> command) {
    return ((SuspensionAware) suspensionAware).onSuspended(command);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static SuspensionAware.@Nullable SuspensionAction onResuming(
      final SuspensionAware<?> suspensionAware, final TypedRecord<?> command) {
    final SuspensionAction action = ((SuspensionAware) suspensionAware).onResuming(command);
    if (action == SuspensionAction.BUFFER) {
      throw new IllegalStateException(
          "Expected PROCESS or REJECT from onResuming, but got BUFFER from processor '%s' for command '%s'."
              .formatted(suspensionAware.getClass().getName(), command.getValueType()));
    }
    return action;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static @Nullable String rejectionReasonFor(
      final SuspensionAction action,
      final SuspensionAware<?> suspensionAware,
      final TypedRecord<?> command,
      final long processInstanceKey) {
    if (action != SuspensionAction.REJECT) {
      return null;
    }
    final String reason =
        ((SuspensionAware) suspensionAware).rejectionReason(command, processInstanceKey);
    return reason != null
        ? reason
        : SuspensionAware.ERROR_MESSAGE_SUSPENDED_PI.formatted(processInstanceKey);
  }

  /**
   * The gate outcome for a command, with the resolved target process instance key.
   *
   * @param outcome the gate action to take
   * @param processInstanceKey the resolved target instance, or {@code -1}
   * @param rejectionReason {@code INVALID_STATE} reason when {@code outcome} is {@code REJECT};
   *     {@code null} otherwise
   */
  public record SuspensionResult(
      SuspensionAction outcome, long processInstanceKey, @Nullable String rejectionReason) {}

  private record InstanceKeys(long processInstanceKey, long elementInstanceKey) {}
}
