/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.distribute;

import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;

public final class DeploymentDistributionBehavior {

  private final DeploymentDistributionRecord deploymentDistributionRecord =
      new DeploymentDistributionRecord();
  private final DeploymentRecord emptyDeploymentRecord = new DeploymentRecord();

  private final List<Integer> otherPartitions;
  private final DeploymentDistributor deploymentDistributor;
  private final ActorControl processingActor;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;

  public DeploymentDistributionBehavior(
      final Writers writers,
      final int partitionsCount,
      final DeploymentDistributor deploymentDistributor,
      final ActorControl processingActor) {
    otherPartitions =
        IntStream.range(Protocol.START_PARTITION_ID, Protocol.START_PARTITION_ID + partitionsCount)
            .filter(partition -> partition != Protocol.DEPLOYMENT_PARTITION)
            .boxed()
            .collect(Collectors.toList());
    this.deploymentDistributor = deploymentDistributor;
    this.processingActor = processingActor;

    stateWriter = writers.state();
    commandWriter = writers.command();
  }

  public void distributeDeployment(final DeploymentRecord deploymentEvent, final long key) {
    final var copiedDeploymentBuffer = BufferUtil.createCopy(deploymentEvent);

    otherPartitions.forEach(
        partitionId -> {
          deploymentDistributionRecord.setPartition(partitionId);
          stateWriter.appendFollowUpEvent(
              key, DeploymentDistributionIntent.DISTRIBUTING, deploymentDistributionRecord);

          distributeDeploymentToPartition(partitionId, key, copiedDeploymentBuffer);
        });

    if (otherPartitions.isEmpty()) {
      // todo(zell): https://github.com/zeebe-io/zeebe/issues/6314
      // we easily reach the record limit if we always write the deployment record
      // since no one consumes currently the FULLY_DISTRIBUTED (only the key) we write an empty
      // record
      stateWriter.appendFollowUpEvent(
          key, DeploymentIntent.FULLY_DISTRIBUTED, emptyDeploymentRecord);
    }
  }

  public void distributeDeploymentToPartition(
      final int partitionId, final long key, final DirectBuffer copiedDeploymentBuffer) {
    final var deploymentPushedFuture =
        deploymentDistributor.pushDeploymentToPartition(key, partitionId, copiedDeploymentBuffer);

    deploymentPushedFuture.onComplete(
        (v, t) ->
            processingActor.runUntilDone(
                () -> {
                  deploymentDistributionRecord.setPartition(partitionId);
                  commandWriter.reset();
                  commandWriter.appendFollowUpCommand(
                      key, DeploymentDistributionIntent.COMPLETE, deploymentDistributionRecord);

                  final long pos = commandWriter.flush();
                  if (pos < 0) {
                    processingActor.yield();
                  } else {
                    processingActor.done();
                  }
                }));
  }
}
