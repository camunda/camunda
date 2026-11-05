/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.streamprocessor.InstanceKeyResolver.InstanceKeys;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionAction;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
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
 * {@code RESUMING}, {@link SuspensionAware#onResuming} classifies instead. Commands targeting
 * {@code SUSPENDING} instances use {@link SuspensionAware#onSuspending}, which delegates to {@link
 * SuspensionAware#onSuspended} by default.
 */
@NullMarked
public final class SuspensionBehavior {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private final ProcessingState processingState;
  private final InstanceKeyResolver resolver;

  public SuspensionBehavior(final ProcessingState processingState) {
    this(processingState, new InstanceKeyResolver(processingState));
  }

  public SuspensionBehavior(
      final ProcessingState processingState, final InstanceKeyResolver resolver) {
    this.processingState = processingState;
    this.resolver = resolver;
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
    return process(command, processor, null);
  }

  /**
   * Same as {@link #process(TypedRecord, TypedRecordProcessor)}, where {@code resolved} are keys
   * the caller already resolved for this command, so the gate does not resolve them twice.
   */
  public SuspensionResult process(
      final TypedRecord<?> command,
      final TypedRecordProcessor<?> processor,
      final @Nullable InstanceKeys resolved) {
    if (!(processor instanceof final SuspensionAware<?> suspensionAware)) {
      // processors that don't opt in via SuspensionAware are never gated; checked before resolving
      // the process instance key to keep the state lookups off the hot path for unrelated commands
      return passThrough(-1);
    }

    final InstanceKeys keys = resolved != null ? resolved : resolver.resolve(command);
    final long processInstanceKey = keys.processInstanceKey();
    if (!keys.isResolved()) {
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
          case SUSPENDING -> onSuspending(suspensionAware, command);
          case null -> SuspensionAction.PROCESS;
        };

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
  private static SuspensionAware.@Nullable SuspensionAction onSuspending(
      final SuspensionAware<?> suspensionAware, final TypedRecord<?> command) {
    return ((SuspensionAware) suspensionAware).onSuspending(command);
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
}
