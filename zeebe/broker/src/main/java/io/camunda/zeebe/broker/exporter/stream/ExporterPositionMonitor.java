/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks each configured exporter's own last-exported position and reports the minimum across all
 * of them to {@link FlowControl}.
 *
 * <p>One instance is shared by every {@link ExporterActor} on a partition (mirroring how {@link
 * ExporterMetrics} is already shared), so {@link #onExported(String, long)} is called concurrently
 * from each exporter's own actor thread.
 */
final class ExporterPositionMonitor {

  private final ConcurrentHashMap<String, Long> positionsByExporterId = new ConcurrentHashMap<>();
  private final FlowControl flowControl;

  ExporterPositionMonitor(final FlowControl flowControl) {
    this.flowControl = flowControl;
  }

  /**
   * Seeds this exporter's starting position, so a newly-started (or not-yet-caught-up) exporter is
   * accounted for in the minimum immediately, rather than only once it exports its first record.
   */
  void recover(final String exporterId, final long position) {
    positionsByExporterId.put(exporterId, position);
    notifyFlowControl();
  }

  void onExported(final String exporterId, final long position) {
    positionsByExporterId.put(exporterId, position);
    notifyFlowControl();
  }

  /**
   * Stops tracking an exporter that was removed or disabled, so it no longer permanently pins the
   * reported minimum once it's no longer part of the exporting set.
   */
  void remove(final String exporterId) {
    positionsByExporterId.remove(exporterId);
    notifyFlowControl();
  }

  private void notifyFlowControl() {
    if (positionsByExporterId.isEmpty()) {
      return;
    }

    long min = Long.MAX_VALUE;
    for (final var position : positionsByExporterId.values()) {
      min = Math.min(min, position);
    }
    // FlowControl#onExported itself ignores non-positive positions, e.g. while some exporter
    // hasn't exported anything yet (VALUE_NOT_FOUND == -1) - matching the pre-decoupling behavior
    // of not throttling until every exporter has exported at least one record.
    flowControl.onExported(min);
  }
}
