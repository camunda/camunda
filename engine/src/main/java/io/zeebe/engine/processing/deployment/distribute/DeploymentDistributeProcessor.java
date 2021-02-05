/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.distribute;

import io.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.immutable.DeploymentState;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class DeploymentDistributeProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final DeploymentState deploymentState;
  private final DeploymentDistributor deploymentDistributor;
  private final ActorControl actor;

  public DeploymentDistributeProcessor(
      final ActorControl actor,
      final DeploymentState deploymentState,
      final DeploymentDistributor deploymentDistributor) {
    this.deploymentState = deploymentState;
    this.deploymentDistributor = deploymentDistributor;
    this.actor = actor;
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
    actor.submit(() -> reprocessPendingDeployments(context.getLogStreamWriter()));
  }

  private void reprocessPendingDeployments(final TypedStreamWriter logStreamWriter) {
    deploymentState.foreachPending(
        ((pendingDeploymentDistribution, key) -> {
          final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
          final DirectBuffer deployment = pendingDeploymentDistribution.getDeployment();
          buffer.putBytes(0, deployment, 0, deployment.capacity());

          distributeDeployment(
              key, pendingDeploymentDistribution.getSourcePosition(), buffer, logStreamWriter);
        }));
  }

  @Override
  public void processRecord(
      final long position,
      final TypedRecord<DeploymentRecord> event,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    final DeploymentRecord deploymentEvent = event.getValue();
    final long key = event.getKey();

    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    deploymentEvent.write(buffer, 0);

    final DirectBuffer bufferView = new UnsafeBuffer();
    bufferView.wrap(buffer, 0, deploymentEvent.getLength());

    // don't distribute the deployment on reprocessing because it may be already distributed (#3124)
    // and it would lead to false-positives in the reprocessing issue detection (#5688)
    // instead, distribute the pending deployments after the reprocessing
    sideEffect.accept(
        () -> {
          distributeDeployment(key, position, bufferView, streamWriter);
          return true;
        });
  }

  private void distributeDeployment(
      final long key,
      final long position,
      final DirectBuffer buffer,
      final TypedStreamWriter logStreamWriter) {
    final ActorFuture<Void> pushDeployment =
        deploymentDistributor.pushDeployment(key, position, buffer);

    actor.runOnCompletion(
        pushDeployment, (aVoid, throwable) -> writeCreatingDeploymentCommand(logStreamWriter, key));
  }

  private void writeCreatingDeploymentCommand(
      final TypedStreamWriter logStreamWriter, final long deploymentKey) {
    final PendingDeploymentDistribution pendingDeploymentDistribution =
        deploymentDistributor.removePendingDeployment(deploymentKey);
    final DirectBuffer buffer = pendingDeploymentDistribution.getDeployment();
    final long sourcePosition = pendingDeploymentDistribution.getSourcePosition();

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord.wrap(buffer);
    final RecordMetadata recordMetadata = new RecordMetadata();
    recordMetadata
        .intent(DeploymentIntent.DISTRIBUTED)
        .valueType(ValueType.DEPLOYMENT)
        .recordType(RecordType.EVENT);

    actor.runUntilDone(
        () -> {
          // we can re-use the write because we are running in the same actor/thread
          logStreamWriter.reset();
          logStreamWriter.configureSourceContext(sourcePosition);
          logStreamWriter.appendFollowUpEvent(
              deploymentKey, DeploymentIntent.DISTRIBUTED, deploymentRecord);

          final long position = logStreamWriter.flush();
          if (position < 0) {
            actor.yield();
          } else {
            actor.done();
          }
        });
  }
}
