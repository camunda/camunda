/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.capabilities;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ClusterCapabilitiesState;
import io.camunda.zeebe.protocol.impl.record.value.capabilities.ClusterCapabilitiesRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClusterCapabilitiesIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.ecv.CapabilitySet;
import io.camunda.zeebe.util.ecv.PartitionCapabilities;
import io.camunda.zeebe.util.ecv.PartitionCapabilities.Progress;
import java.time.Duration;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Gets the cluster's commit decision into this partition (design section 3), and processes the
 * resulting commit command.
 *
 * <p>As leader, it compares the enabled capabilities in the engine state with the local view of the
 * cluster's enabled capabilities, when it becomes leader and each time the view grows. If the state
 * does not contain all of the view, it writes a {@link ClusterCapabilitiesIntent#COMMIT} command
 * with the view's capabilities to the log of this partition. The command is applied as a union, so
 * writing it twice, or an older set, has no effect. It does not write the same set again after the
 * engine rejected it.
 *
 * <p>It does not write a set that this broker does not support (design open point 10, current
 * proposal): another broker that supports it becomes leader and writes it.
 */
public final class ClusterCapabilitiesCommitter
    implements TypedRecordProcessor<ClusterCapabilitiesRecord>, StreamProcessorLifecycleAware {
  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private final ClusterCapabilitiesState state;
  private final PartitionCapabilities partitionCapabilities;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final KeyGenerator keyGenerator;
  private final Consumer<CapabilitySet> viewListener = ignored -> scheduleCheck();

  private volatile @Nullable ProcessingScheduleService scheduleService;
  // the applied set as of the last change on the processing actor; the check runs on the async
  // processing actor, which must not read the processing state concurrently
  private volatile CapabilitySet applied = CapabilitySet.EMPTY;
  // only accessed by the check, on the async processing actor
  private @Nullable CapabilitySet lastWritten;
  private @Nullable CapabilitySet lastUnsupported;
  // only accessed on the processing actor
  private @Nullable CapabilitySet rejected;
  private @Nullable String rejectionReason;

  public ClusterCapabilitiesCommitter(
      final ClusterCapabilitiesState state,
      final PartitionCapabilities partitionCapabilities,
      final Writers writers,
      final KeyGenerator keyGenerator) {
    this.state = state;
    this.partitionCapabilities = partitionCapabilities;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    scheduleService = context.getScheduleService();
    lastWritten = null;
    applied = state.getEnabledCapabilities();
    reportProgress();
    partitionCapabilities.view().addListener(viewListener);
    scheduleCheck();
  }

  @Override
  public void onClose() {
    stop();
  }

  @Override
  public void onFailed() {
    stop();
  }

  @Override
  public void onPaused() {
    stop();
  }

  @Override
  public void onResumed() {
    applied = state.getEnabledCapabilities();
    partitionCapabilities.view().addListener(viewListener);
    reportProgress();
    scheduleCheck();
  }

  @Override
  public void processRecord(final TypedRecord<ClusterCapabilitiesRecord> command) {
    final var capabilities = CapabilitySet.decode(command.getValue().getEnabledCapabilities());

    final var missingPrerequisites = capabilities.missingPrerequisites();
    if (!missingPrerequisites.isEmpty()) {
      reject(
          command,
          capabilities,
          "Expected the enabled capabilities %s to contain the prerequisites of each capability, but: %s"
              .formatted(capabilities, String.join("; ", missingPrerequisites)));
      return;
    }

    final var unsupported = capabilities.minus(state.getSupportedCapabilities());
    if (!unsupported.isEmpty()) {
      reject(
          command,
          capabilities,
          "Expected the enabled capabilities %s to be supported by this broker, but it does not support %s"
              .formatted(capabilities, unsupported));
      return;
    }

    final var committed =
        new ClusterCapabilitiesRecord()
            .setEnabledCapabilities(state.getEnabledCapabilities().union(capabilities).encode());
    stateWriter.appendFollowUpEvent(
        keyGenerator.nextKey(), ClusterCapabilitiesIntent.COMMITTED, committed);
    applied = state.getEnabledCapabilities();
    reportProgress();
  }

  private void reject(
      final TypedRecord<ClusterCapabilitiesRecord> command,
      final CapabilitySet capabilities,
      final String reason) {
    rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, reason);
    rejected = capabilities;
    rejectionReason = reason;
    LOG.warn("Rejected the commit of the enabled capabilities: {}", reason);
    reportProgress();
  }

  private void stop() {
    scheduleService = null;
    try {
      partitionCapabilities.view().removeListener(viewListener);
      partitionCapabilities.onLeadershipLost();
    } catch (final RuntimeException e) {
      // the ECV bookkeeping must never block a role transition of the partition
      LOG.warn("Failed to stop reporting the ECV progress of this partition", e);
    }
  }

  /** Called on any thread; the check itself runs on the processing actor. */
  private void scheduleCheck() {
    final var service = scheduleService;
    if (service != null) {
      service.runDelayed(Duration.ZERO, this::check);
    }
  }

  private TaskResult check(final TaskResultBuilder resultBuilder) {
    final var view = partitionCapabilities.view().enabled();
    if (applied.containsAll(view) || view.equals(lastWritten)) {
      return resultBuilder.build();
    }

    final var unsupported = view.minus(partitionCapabilities.supported());
    if (!unsupported.isEmpty()) {
      if (!view.equals(lastUnsupported)) {
        LOG.warn(
            "Not committing the enabled capabilities {} to this partition, as this broker does not"
                + " support {}; a leader that supports them commits them",
            view,
            unsupported);
        lastUnsupported = view;
      }
      return resultBuilder.build();
    }

    if (resultBuilder.appendCommandRecord(
        ClusterCapabilitiesIntent.COMMIT,
        new ClusterCapabilitiesRecord().setEnabledCapabilities(view.encode()))) {
      lastWritten = view;
    }
    return resultBuilder.build();
  }

  private void reportProgress() {
    try {
      partitionCapabilities.onProgress(new Progress(applied, rejected, rejectionReason));
    } catch (final RuntimeException e) {
      LOG.warn("Failed to report the ECV progress of this partition", e);
    }
  }
}
