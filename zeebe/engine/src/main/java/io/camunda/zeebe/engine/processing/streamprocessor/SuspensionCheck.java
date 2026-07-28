/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The primary suspension gate: classifies how a command should be treated while its target carries
 * a suspension marker.
 *
 * <p>Classification is origin-based by default — external (user-triggered) commands are rejected,
 * internal (engine-written) commands are buffered — and a processor's {@link SuspensionAware}
 * override takes precedence.
 *
 * <p>Kept scope-neutral on purpose: today suspension only exists at the process-instance scope, but
 * the public {@link #resolve} contract does not assume that. Future suspension scopes (e.g. process
 * definition or element) plug into {@link #resolveSuspensionMarker} without touching callers.
 */
@NullMarked
public final class SuspensionCheck {

  private final ProcessingState processingState;

  public SuspensionCheck(final ProcessingState processingState) {
    this.processingState = processingState;
  }

  /**
   * Classifies the command; commands with no suspended target are always processed. The resolved
   * target process instance key is returned alongside the decision so callers reuse it rather than
   * re-deriving it (external {@code JOB}/{@code INCIDENT}/{@code USER_TASK} commands don't carry it
   * on the wire).
   */
  public Result resolve(final TypedRecord<?> command, final TypedRecordProcessor<?> processor) {
    final long processInstanceKey = resolveProcessInstanceKey(command);
    if (processInstanceKey <= 0) {
      return new Result(Decision.PROCESS, processInstanceKey);
    }

    final State marker =
        processingState.getSuspensionState().getSuspensionState(processInstanceKey);
    if (marker == null) {
      return new Result(Decision.PROCESS, processInstanceKey);
    }

    final Decision decision =
        switch (classify(command, processor)) {
          case PROCESS -> Decision.PROCESS;
          case REJECT -> Decision.REJECT;
          case BUFFER ->
              marker == State.SUSPENDED
                  ? Decision.BUFFER
                  // RESUMING: pass through so drained commands can execute (see #57792).
                  : Decision.PROCESS;
        };
    return new Result(decision, processInstanceKey);
  }

  /**
   * A processor's explicit {@link SuspensionAware} classification wins; otherwise the origin-based
   * default applies: external (user-triggered) commands are rejected, internal (engine-written)
   * commands are buffered.
   */
  private static SuspensionBehavior classify(
      final TypedRecord<?> command, final TypedRecordProcessor<?> processor) {
    SuspensionBehavior behavior = null;
    if (processor instanceof final SuspensionAware<?> suspensionAware) {
      behavior = suspensionBehavior(suspensionAware, command);
    }
    if (behavior == null) {
      behavior =
          command.isInternalCommand() ? SuspensionBehavior.BUFFER : SuspensionBehavior.REJECT;
    }
    // BUFFER is only safe for internal commands: buffered records carry no requestId and write no
    // response, so an external BUFFER would block the client until the gateway times out and could
    // never be answered on drain. Fail fast instead.
    if (behavior == SuspensionBehavior.BUFFER && !command.isInternalCommand()) {
      throw new IllegalStateException(
          String.format(
              "Command '%s' with intent '%s' was classified BUFFER but is external; buffering is"
                  + " only supported for internal commands. Override suspensionBehavior to REJECT or"
                  + " PROCESS instead.",
              command.getValueType(), command.getIntent()));
    }
    return behavior;
  }

  /**
   * Resolves the process instance a command targets. Most values carry their own {@code
   * processInstanceKey}; external {@code JOB}, {@code INCIDENT} and {@code USER_TASK} commands only
   * carry the entity key, so the persisted entity is consulted. Returns {@code -1} when it can't be
   * resolved.
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
      default -> -1;
    };
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static @Nullable SuspensionBehavior suspensionBehavior(
      final SuspensionAware<?> suspensionAware, final TypedRecord<?> command) {
    return ((SuspensionAware) suspensionAware).suspensionBehavior(command);
  }

  /** The gate outcome for a command, with the resolved target process instance key. */
  public record Result(Decision decision, long processInstanceKey) {}

  /** The outcome of the suspension gate for a single command. */
  public enum Decision {
    PROCESS,
    REJECT,
    BUFFER
  }
}
