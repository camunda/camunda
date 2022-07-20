/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.distribute;

import io.camunda.zeebe.engine.api.LegacyTask;
import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.LegacyTypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
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

  private final DeploymentDistributor deploymentDistributor;
  private final ProcessingScheduleService scheduleService;
  private final StateWriter stateWriter;

  public DeploymentDistributionBehavior(
      final Writers writers,
      final int partitionsCount,
      final DeploymentDistributionCommandSender deploymentDistributionCommandSender,
      final DeploymentDistributor deploymentDistributor,
      final ProcessingScheduleService scheduleService) {
    otherPartitions =
        IntStream.range(Protocol.START_PARTITION_ID, Protocol.START_PARTITION_ID + partitionsCount)
            .filter(partition -> partition != Protocol.DEPLOYMENT_PARTITION)
            .boxed()
            .collect(Collectors.toList());
    this.deploymentDistributionCommandSender = deploymentDistributionCommandSender;
    this.deploymentDistributor = deploymentDistributor;
    this.scheduleService = scheduleService;

    stateWriter = writers.state();
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

    scheduleService.runOnCompletion(
        deploymentPushedFuture, new WriteDeploymentDistributionCompleteTask(partitionId, key));
  }

  private static final class WriteDeploymentDistributionCompleteTask implements LegacyTask {

    private final DeploymentDistributionRecord deploymentDistributionRecord =
        new DeploymentDistributionRecord();

    private final int partitionId;
    private final long key;

    private WriteDeploymentDistributionCompleteTask(final int partitionId, final long key) {
      this.partitionId = partitionId;
      this.key = key;
    }

    @Override
    public void run(
        final LegacyTypedCommandWriter commandWriter,
        final ProcessingScheduleService schedulingService) {

      deploymentDistributionRecord.setPartition(partitionId);
      commandWriter.reset();
      commandWriter.appendFollowUpCommand(
          key, DeploymentDistributionIntent.COMPLETE, deploymentDistributionRecord);

      final long pos = commandWriter.flush();
      if (pos < 0) {
        schedulingService.runDelayed(Duration.ofMillis(100), this);
      }
    }
  }
}
