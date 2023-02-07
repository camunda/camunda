/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.distribute;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;

public final class DeploymentDistributionBehavior {

  private final DeploymentDistributionRecord deploymentDistributionRecord =
      new DeploymentDistributionRecord();
  private final DeploymentRecord emptyDeploymentRecord = new DeploymentRecord();

  private final List<Integer> otherPartitions;
  private final DeploymentDistributionCommandSender deploymentDistributionCommandSender;

  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;

  public DeploymentDistributionBehavior(
      final Writers writers,
      final int partitionsCount,
      final DeploymentDistributionCommandSender deploymentDistributionCommandSender) {
    otherPartitions =
        IntStream.range(Protocol.START_PARTITION_ID, Protocol.START_PARTITION_ID + partitionsCount)
            .filter(partition -> partition != Protocol.DEPLOYMENT_PARTITION)
            .boxed()
            .collect(Collectors.toList());
    this.deploymentDistributionCommandSender = deploymentDistributionCommandSender;

    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
  }

  public void distributeDeployment(final DeploymentRecord deploymentEvent, final long key) {
    final var copiedDeploymentBuffer = BufferUtil.createCopy(deploymentEvent);

    otherPartitions.forEach(
        partitionId -> {
          deploymentDistributionRecord.setPartition(partitionId);
          stateWriter.appendFollowUpEvent(
              key, DeploymentDistributionIntent.DISTRIBUTING, deploymentDistributionRecord);

          sideEffectWriter.appendSideEffect(
              () -> {
                distributeDeploymentToPartition(key, partitionId, copiedDeploymentBuffer);
                return true;
              });
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
      final long key, final int partitionId, final DirectBuffer copiedDeploymentBuffer) {
    final var deploymentRecord = new DeploymentRecord();
    deploymentRecord.wrap(copiedDeploymentBuffer);
    deploymentDistributionCommandSender.distributeToPartition(key, partitionId, deploymentRecord);
  }
}
