/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ExporterPurgeException;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.StreamClock;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClusterChangeExecutorImpl implements ClusterChangeExecutor {
  private static final Logger LOG = LoggerFactory.getLogger(ClusterChangeExecutorImpl.class);

  private final ConcurrencyControl concurrencyControl;
  private final ExporterRepository exporterRepository;
  private final NodeIdProvider nodeIdProvider;
  private final MeterRegistry meterRegistry;

  public ClusterChangeExecutorImpl(
      final ConcurrencyControl concurrencyControl,
      final ExporterRepository exporterRepository,
      final NodeIdProvider nodeIdProvider,
      final MeterRegistry meterRegistry) {
    this.concurrencyControl = concurrencyControl;
    this.exporterRepository = exporterRepository;
    this.nodeIdProvider = Objects.requireNonNull(nodeIdProvider);
    this.meterRegistry = meterRegistry;
  }

  @Override
  public ActorFuture<Void> deleteHistory() {
    final ActorFuture<Void> result = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            exporterRepository.getExporters().forEach(this::purgeExporter);
            result.complete(null);
          } catch (final Exception e) {
            result.completeExceptionally(e);
          }
        });

    return result;
  }

  @Override
  public ActorFuture<Void> preScaling(
      final int currentClusterSize, final Set<MemberId> clusterMembers) {
    final ActorFuture<Void> result = concurrencyControl.createFuture();

    if (currentClusterSize >= clusterMembers.size()) {
      // No scaling up, so no need to call the NodeIdProvider
      result.complete(null);
      return result;
    }

    concurrencyControl.run(
        () -> {
          try {
            nodeIdProvider
                .scale(clusterMembers.size())
                .thenAccept(ignore -> result.complete(null))
                .exceptionally(
                    e -> {
                      result.completeExceptionally(e);
                      return null;
                    });
          } catch (final Exception e) {
            result.completeExceptionally(e);
          }
        });

    return result;
  }

  @Override
  public ActorFuture<Void> postScaling(final Set<MemberId> clusterMembers) {
    final ActorFuture<Void> result = concurrencyControl.createFuture();
    // Here it is ok to execute even if the cluster size did not change.
    // For scale up this will be a no-op as the leases are already created, and for scale down
    // additional leases will be removed.
    concurrencyControl.run(
        () -> {
          try {
            nodeIdProvider
                .scale(clusterMembers.size())
                .thenAccept(ignore -> result.complete(null))
                .exceptionally(
                    e -> {
                      result.completeExceptionally(e);
                      return null;
                    });
          } catch (final Exception e) {
            result.completeExceptionally(e);
          }
        });

    return result;
  }

  private void purgeExporter(final String id, final ExporterDescriptor descriptor) {
    final Exporter exporter = descriptor.newInstance();

    final var exporterContext =
        new ExporterContext(
            Loggers.getExporterLogger(descriptor.getId()),
            descriptor.getConfiguration(),
            1,
            meterRegistry,
            StreamClock.system());

    try {
      exporter.configure(exporterContext);
      exporter.purge();
      LOG.info("Purged history for {}", id);
      exporter.close();
    } catch (final Exception e) {
      throw new ExporterPurgeException(
          "Failed to purge C8 data from exporter %s of type %s; operation will be retried."
              .formatted(id, exporter.getClass()),
          e);
    }
  }
}
