/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.distribute;

import static io.camunda.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;

import io.camunda.zeebe.engine.transport.InterPartitionCommandSender;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import org.agrona.DirectBuffer;

public final class DeploymentDistributionCommandSender {
  private final InterPartitionCommandSender sender;
  private final int partitionId;

  public DeploymentDistributionCommandSender(
      final int partitionId, final InterPartitionCommandSender sender) {
    this.partitionId = partitionId;
    this.sender = sender;
  }

  public void distributeToPartition(
      final long key, final int receiverPartitionId, final DeploymentRecord deploymentRecord) {
    sender.sendCommand(
        receiverPartitionId,
        ValueType.DEPLOYMENT,
        DeploymentIntent.DISTRIBUTE,
        key,
        deploymentRecord);
  }

  public void completeOnPartition(final long deploymentKey, final DirectBuffer tenantId) {
    final var distributionRecord = new DeploymentDistributionRecord();
    distributionRecord.setPartition(partitionId).setTenantId(tenantId);
    sender.sendCommand(
        DEPLOYMENT_PARTITION,
        ValueType.DEPLOYMENT_DISTRIBUTION,
        DeploymentDistributionIntent.COMPLETE,
        deploymentKey,
        distributionRecord);
  }
}
