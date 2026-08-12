/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.dynamic.config.changes.ExporterPurgeException;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.stream.api.StreamClock;
import io.micrometer.core.instrument.MeterRegistry;
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Purges the history exported by one physical tenant, by invoking {@link Exporter#purge()} on every
 * exporter configured for that tenant.
 */
public final class ExporterHistoryPurger {
  private static final Logger LOG = LoggerFactory.getLogger(ExporterHistoryPurger.class);

  private final String physicalTenantId;
  private final ExporterRepository exporterRepository;
  private final MeterRegistry meterRegistry;

  public ExporterHistoryPurger(
      final String physicalTenantId,
      final ExporterRepository exporterRepository,
      final MeterRegistry meterRegistry) {
    this.physicalTenantId = physicalTenantId;
    this.exporterRepository = exporterRepository;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Purges the history of every exporter of this physical tenant.
   *
   * @throws ExporterPurgeException if an exporter fails to purge, in which case an unknown subset
   *     of the exporters has been purged already
   */
  public void purgeAll() {
    exporterRepository.getExporters().forEach(this::purgeExporter);
  }

  private void purgeExporter(final String id, final ExporterDescriptor descriptor) {
    final Exporter exporter = descriptor.newInstance();

    // The exporter is closed before the context, as closing it may deregister meters from the
    // context's registry. Both are released even when purging fails, because the operation is
    // retried until it succeeds and an exporter may well have opened clients before failing.
    try (final var exporterContext =
        new ExporterContext(
            Loggers.getExporterLogger(descriptor.getId()),
            descriptor.getConfiguration(),
            new PartitionId(physicalTenantId, 1),
            "",
            exporterRepository.getLicenseKey(),
            meterRegistry,
            StreamClock.system())) {
      try {
        exporter.configure(exporterContext);
        exporter.purge();
        LOG.info("Purged history of physical tenant {} for {}", physicalTenantId, id);
      } catch (final Exception e) {
        throw new ExporterPurgeException(
            "Failed to purge C8 data from exporter %s of type %s; operation will be retried."
                .formatted(id, exporter.getClass()),
            e);
      } finally {
        CloseHelper.close(
            error -> LOG.warn("Failed to close exporter {} after purging; ignoring", id, error),
            exporter::close);
      }
    }
  }
}
