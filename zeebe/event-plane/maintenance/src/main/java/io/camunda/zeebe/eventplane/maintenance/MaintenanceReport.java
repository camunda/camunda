/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

/**
 * What a run of maintenance did.
 *
 * @param rollupsWritten the rollups it PUT
 * @param segmentsDeleted the segments it deleted: rollups' inputs, segments that a rollup already
 *     covers, rollups that an earlier one covers, and segments that retention dropped
 * @param markersWritten the retention markers it PUT
 * @param snapshotsDeleted the snapshots that retention dropped, and the snapshot directories
 *     without a descriptor that it cleaned up
 */
public record MaintenanceReport(
    int rollupsWritten, int segmentsDeleted, int markersWritten, int snapshotsDeleted) {

  public static final MaintenanceReport NONE = new MaintenanceReport(0, 0, 0, 0);

  /** A report of a run that deleted no snapshot. */
  public MaintenanceReport(
      final int rollupsWritten, final int segmentsDeleted, final int markersWritten) {
    this(rollupsWritten, segmentsDeleted, markersWritten, 0);
  }

  /** The two reports' counts added up. */
  public MaintenanceReport plus(final MaintenanceReport other) {
    return new MaintenanceReport(
        rollupsWritten + other.rollupsWritten,
        segmentsDeleted + other.segmentsDeleted,
        markersWritten + other.markersWritten,
        snapshotsDeleted + other.snapshotsDeleted);
  }
}
