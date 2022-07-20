/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.distribute;

import io.camunda.zeebe.engine.transport.InterPartitionCommandSender;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;

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
}
