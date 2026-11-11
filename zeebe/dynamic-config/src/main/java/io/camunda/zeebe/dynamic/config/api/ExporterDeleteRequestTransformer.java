/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;

/**
 * Deletes an exporter from every member/partition that still has it. Members or partitions that
 * don't list the exporter are skipped: after a configuration drift, the exporter may remain on only
 * some members, and requiring it everywhere would make it impossible to delete. Whether the
 * exporter is in a deletable state is validated per partition by the operation applier.
 */
public final class ExporterDeleteRequestTransformer implements ConfigurationChangeRequest {

  private final String exporterId;

  public ExporterDeleteRequestTransformer(final String exporterId) {

    this.exporterId = exporterId;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();
    for (final var member : clusterConfiguration.members().entrySet()) {
      final var memberId = member.getKey();
      for (final var partitions : member.getValue().partitions().entrySet()) {
        final var partitionId = partitions.getKey();
        final var config = partitions.getValue().config();
        final var hasExporter =
            config.isInitialized() && config.exporting().exporters().containsKey(exporterId);
        if (hasExporter) {
          operations.add(new PartitionDeleteExporterOperation(memberId, partitionId, exporterId));
        }
      }
    }
    if (operations.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              "Expected to delete exporter '%s', but no partition has this exporter"
                  .formatted(exporterId)));
    }
    return Either.right(operations);
  }
}
