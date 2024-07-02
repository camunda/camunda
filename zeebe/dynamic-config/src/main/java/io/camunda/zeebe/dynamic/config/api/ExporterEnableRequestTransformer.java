/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ExporterEnableRequestTransformer implements ConfigurationChangeRequest {

  private final String exporterId;
  private final Optional<String> initializeFrom;

  public ExporterEnableRequestTransformer(
      final String exporterId, final Optional<String> initializeFrom) {
    this.exporterId = exporterId;
    this.initializeFrom = initializeFrom;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();
    for (final var member : clusterConfiguration.members().entrySet()) {
      final var memberId = member.getKey();
      for (final var partitions : member.getValue().partitions().entrySet()) {
        final var partitionId = partitions.getKey();
        operations.add(
            new PartitionEnableExporterOperation(
                memberId, partitionId, exporterId, initializeFrom));
      }
    }
    return Either.right(operations);
  }
}
