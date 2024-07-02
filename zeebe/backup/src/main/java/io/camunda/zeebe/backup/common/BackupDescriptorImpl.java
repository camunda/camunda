/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.dynamic.config.state.RoutingConfiguration;
import java.util.Optional;

public record BackupDescriptorImpl(
    Optional<String> snapshotId,
    long checkpointPosition,
    int numberOfPartitions,
    String brokerVersion,
    RoutingConfiguration routingConfiguration)
    implements BackupDescriptor {

  public static BackupDescriptorImpl from(final BackupDescriptor descriptor) {
    return new BackupDescriptorImpl(
        descriptor.snapshotId(),
        descriptor.checkpointPosition(),
        descriptor.numberOfPartitions(),
        descriptor.brokerVersion(),
        descriptor.routingConfiguration());
  }
}
