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
import io.zeebe.logstreams.log.LogStreamWriterImpl;
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

public class DeploymentDistributeProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final LogStreamWriterImpl logStreamWriter;
  private final DeploymentsState deploymentsState;
  private final DeploymentDistributor deploymentDistributor;
  private ActorControl actor;

  public DeploymentDistributeProcessor(
      final DeploymentsState deploymentsState,
      final LogStreamWriterImpl logStreamWriter,
      final DeploymentDistributor deploymentDistributor) {
    this.deploymentsState = deploymentsState;
    this.logStreamWriter = logStreamWriter;
    this.deploymentDistributor = deploymentDistributor;
  }

  @Override
  public void onOpen(final ReadonlyProcessingContext processingContext) {
    actor = processingContext.getActor();
    actor.submit(this::reprocessPendingDeployments);
  }

  private void reprocessPendingDeployments() {
    deploymentsState.foreachPending(
        ((pendingDeploymentDistribution, key) -> {
          final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
          final DirectBuffer deployment = pendingDeploymentDistribution.getDeployment();
          buffer.putBytes(0, deployment, 0, deployment.capacity());

          distributeDeployment(key, pendingDeploymentDistribution.getSourcePosition(), buffer);
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
    distributeDeployment(key, position, buffer);
  }

  private void distributeDeployment(
      final long key, final long position, final DirectBuffer buffer) {
    final ActorFuture<Void> pushDeployment =
        deploymentDistributor.pushDeployment(key, position, buffer);

    actor.runOnCompletion(
        pushDeployment, (aVoid, throwable) -> writeCreatingDeploymentCommand(key));
  }

  private void writeCreatingDeploymentCommand(final long deploymentKey) {
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
          final long position =
              logStreamWriter
                  .key(deploymentKey)
                  .sourceRecordPosition(sourcePosition)
                  .valueWriter(deploymentRecord)
                  .metadataWriter(recordMetadata)
                  .tryWrite();
          if (position < 0) {
            actor.yield();
          } else {
            actor.done();
          }
        });
  }
}
