/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.history.Histories;
import io.camunda.zeebe.eventplane.history.HistoryLineage;
import io.camunda.zeebe.eventplane.history.HistoryManifest;
import io.camunda.zeebe.eventplane.history.InconsistentHistoryException;
import io.camunda.zeebe.eventplane.history.InvalidHistoryJsonException;
import io.camunda.zeebe.eventplane.history.MissingManifestException;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.snapshot.InvalidSnapshotDescriptorException;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.time.InstantSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintains every log in an object store: it merges old segments into rollups, and then, if
 * configured, deletes what is older than the retention period.
 *
 * <p>Run one at a time for a store; a second one at once costs only duplicate work, which is
 * cleaned up, but no data. A run can stop anywhere: it deletes only after what replaces the deleted
 * is in the store, and the next run finishes the job.
 */
public final class Maintenance {

  private static final Logger LOG = LoggerFactory.getLogger(Maintenance.class);

  private final ObjectStore store;
  private final StoreLayout layout;
  private final Rollups rollups;
  private final @Nullable Retention retention;

  public Maintenance(
      final ObjectStore store, final MaintenanceConfig config, final InstantSource clock) {
    this.store = store;
    layout = new StoreLayout(store);
    rollups = new Rollups(store, config, clock);
    final var retentionPeriod = config.retention();
    retention =
        retentionPeriod == null ? null : new Retention(store, layout, retentionPeriod, clock);
  }

  /**
   * Maintains every log of the store once: for each, the rollups of its histories, then retention.
   * A segment that is not as its name says, and a history that can't be resolved yet, are logged
   * and left alone, so that they don't hold up the other logs.
   *
   * @throws IOException if the store fails
   */
  public MaintenanceReport runOnce() throws IOException {
    var report = MaintenanceReport.NONE;
    for (final var cluster : layout.clusters()) {
      for (final var tenant : layout.tenants(cluster)) {
        report = report.plus(runTenant(cluster, tenant));
      }
    }
    LOG.info("Maintained the store {}: {}", store.root(), report);
    return report;
  }

  private MaintenanceReport runTenant(final String cluster, final String tenant)
      throws IOException {
    final var resolved = retention == null ? null : resolve(cluster, tenant);
    var report = MaintenanceReport.NONE;
    for (final var log : layout.logs(cluster, tenant)) {
      for (final var history : layout.histories(log)) {
        report = report.plus(rollups.run(log, history));
      }
      if (retention != null && resolved != null) {
        report = report.plus(retain(retention, log, resolved));
      }
    }
    return report;
  }

  /** The tenant's histories and its current one, or null if retention has to wait for them. */
  private @Nullable Resolved resolve(final String cluster, final String tenant) throws IOException {
    try {
      final var lineage = Histories.load(store, cluster, tenant);
      final var current = lineage.current(cluster, tenant);
      if (current.isEmpty()) {
        LOG.info("Not applying retention to {}/{}, which has no history yet", cluster, tenant);
        return null;
      }
      return new Resolved(lineage, current.get());
    } catch (final InconsistentHistoryException | InvalidHistoryJsonException e) {
      LOG.warn(
          "Not applying retention to {}/{}, as its histories are not as expected",
          cluster,
          tenant,
          e);
      return null;
    }
  }

  private static MaintenanceReport retain(
      final Retention retention, final LogId log, final Resolved resolved) throws IOException {
    try {
      return retention.run(log, resolved.current(), resolved.lineage());
    } catch (final MissingManifestException | InconsistentHistoryException e) {
      LOG.warn("Not applying retention to {}, as its histories are not as expected", log, e);
      return MaintenanceReport.NONE;
    } catch (final InvalidSnapshotDescriptorException e) {
      LOG.warn("Not applying retention to {}, as a snapshot descriptor is not valid", log, e);
      return MaintenanceReport.NONE;
    }
  }

  /** A tenant's histories, and the one that is current. */
  private record Resolved(HistoryLineage lineage, HistoryManifest current) {}
}
