/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

public interface SnapshotMetadata {

  /**
   * @return version of the snapshot
   */
  int version();

  /**
   * @return processed position in the snapshot (same as in SnapshotId)
   */
  long processedPosition();

  /**
   * @return exported position in the snapshot (same as in SnapshotId)
   */
  long exportedPosition();

  /**
   * A snapshot is only valid if the logstream consists of the events from the processedPosition up
   * to the followup event position.
   *
   * @return position of the last followUpEvent that must be in the logstream to ensure that the
   *     system can recover from the snapshot and the logstream.
   */
  long lastFollowupEventPosition();
}
