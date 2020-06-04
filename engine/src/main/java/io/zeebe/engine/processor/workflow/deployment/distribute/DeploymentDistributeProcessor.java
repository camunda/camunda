/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.distribute;

import io.zeebe.engine.processor.ReadonlyProcessingContext;
import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.deployment.DeploymentsState;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Optional;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class DeploymentDistributeProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final DeploymentsState deploymentsState;
  private final DeploymentDistributor deploymentDistributor;
  private final ActorControl actor;

  public DeploymentDistributeProcessor(
      final ActorControl actor,
      final DeploymentsState deploymentsState,
      final DeploymentDistributor deploymentDistributor) {
    this.deploymentsState = deploymentsState;
    this.deploymentDistributor = deploymentDistributor;
    this.actor = actor;
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
    actor.submit(() -> reprocessPendingDeployments(context.getLogStreamWriter()));
  }

  private void reprocessPendingDeployments(final TypedStreamWriter logStreamWriter) {
    deploymentsState.foreachPending(
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

    distributeDeployment(key, position, bufferView, streamWriter);
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

          final Optional<ActorFuture<Long>> optPosition = logStreamWriter.flush();
          if (optPosition.isPresent()) {
            actor.done();
          } else {
            actor.yield();
          }
        });
  }
}
