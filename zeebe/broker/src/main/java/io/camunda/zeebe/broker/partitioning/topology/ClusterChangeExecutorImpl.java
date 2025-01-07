/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.clock.DefaultActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class ClusterChangeExecutorImpl implements ClusterChangeExecutor {

  private final ConcurrencyControl concurrencyControl;
  private final ExporterRepository exporterRepository;

  public ClusterChangeExecutorImpl(
      final ConcurrencyControl concurrencyControl, final ExporterRepository exporterRepository) {
    this.concurrencyControl = concurrencyControl;
    this.exporterRepository = exporterRepository;
  }

  @Override
  public ActorFuture<Void> deleteHistory() {
    return purgeExporters();
  }

  private ActorFuture<Void> purgeExporters() {
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

  private void purgeExporter(final String id, final ExporterDescriptor descriptor) {
    final Exporter exporter = descriptor.newInstance();
    final var clock = new DefaultActorClock();
    final var meterRegistry = new SimpleMeterRegistry();

    final var exporterContext =
        new ExporterContext(
            Loggers.getExporterLogger(descriptor.getId()),
            descriptor.getConfiguration(),
            1,
            meterRegistry,
            clock);
    try {
      exporter.configure(exporterContext);
      exporter.purge();
      exporter.close();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
