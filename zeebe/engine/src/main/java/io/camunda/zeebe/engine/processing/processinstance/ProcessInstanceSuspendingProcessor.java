/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.time.InstantSource;

/** Completes a validated suspend synchronously after the {@code SUSPENDING} event is applied. */
@ExcludeAuthorizationCheck
final class ProcessInstanceSuspendingProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord>, SuspensionAware<ProcessInstanceRecord> {

  private final ElementInstanceState elementInstanceState;
  private final TypedResponseWriter responseWriter;
  private final StateWriter stateWriter;
  private final ProcessInstanceSuspensionJobBehavior suspensionJobBehavior;
  private final ProcessInstanceSuspensionMessageSubscriptionBehavior suspensionSubscriptionBehavior;
  private final SuspensionMetrics suspensionMetrics;

  ProcessInstanceSuspendingProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final SubscriptionCommandSender subscriptionCommandSender,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final InstantSource clock,
      final SuspensionMetrics suspensionMetrics) {
    elementInstanceState = processingState.getElementInstanceState();
    responseWriter = writers.response();
    stateWriter = writers.state();
    suspensionJobBehavior =
        new ProcessInstanceSuspensionJobBehavior(
            elementInstanceState, processingState.getJobState(), stateWriter);
    suspensionSubscriptionBehavior =
        new ProcessInstanceSuspensionMessageSubscriptionBehavior(
            elementInstanceState,
            processingState.getProcessMessageSubscriptionState(),
            stateWriter,
            writers.sideEffect(),
            subscriptionCommandSender,
            transientProcessMessageSubscriptionState,
            clock);
    this.suspensionMetrics = suspensionMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final long processInstanceKey = command.getKey();
    final ProcessInstanceRecord value =
        elementInstanceState.getInstance(processInstanceKey).getValue();
    final int suspendedJobCount = closeSubscriptionsAndSuspendJobs(processInstanceKey);
    stateWriter.appendFollowUpEvent(processInstanceKey, ProcessInstanceIntent.SUSPENDED, value);
    responseWriter.writeAcceptedResponseOnCommand(
        processInstanceKey, ProcessInstanceIntent.SUSPENDED, value, command);
    suspensionMetrics.instanceSuspended();
    if (suspendedJobCount > 0) {
      suspensionMetrics.jobsSuspended(suspendedJobCount);
    }
  }

  /**
   * Keep this order, closing subscriptions first keeps RocksDB seeks cheap. Currently, subscription
   * closures visit all element instance subscriptions which runs a RocksDB seek command. If the
   * order is reversed, job suspensions will write to the transaction batch first, which requires
   * the seek command to also check against those batched writes. See <a
   * href="https://github.com/camunda/camunda/issues/62933">#62933</a>.
   */
  private int closeSubscriptionsAndSuspendJobs(final long processInstanceKey) {
    suspensionSubscriptionBehavior.closeSubscriptions(processInstanceKey);
    return suspensionJobBehavior.suspendJobs(processInstanceKey);
  }

  @Override
  public SuspensionAction onSuspended(final TypedRecord<ProcessInstanceRecord> record) {
    // The decision gate for suspension is in ProcessInstanceSuspendProcessor. This processor is
    // only called after the SUSPENDING event is written, so we can always process the command.
    return SuspensionAction.PROCESS;
  }

  @Override
  public SuspensionAction onResuming(final TypedRecord<ProcessInstanceRecord> record) {
    return SuspensionAction.PROCESS;
  }
}
