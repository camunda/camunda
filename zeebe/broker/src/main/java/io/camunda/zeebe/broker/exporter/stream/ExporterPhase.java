/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

// The PAUSED phase is when the exporter is paused, and the exporter is not exporting records.
// The SOFT_PAUSED phase is when we keep exporting the records without updating the exporter state.
// This means that after a restart, the exporter will continue from the last persisted position,
// re-exporting every record that was processed while soft-paused. This is used in the backup
// process to ensure that after restoring Zeebe, the secondary storage will receive all missing
// records from Zeebe via re-export, bridging any gap between the Zeebe and secondary storage
// backups.
public enum ExporterPhase {
  EXPORTING,
  PAUSED,
  SOFT_PAUSED,
  CLOSED
}
