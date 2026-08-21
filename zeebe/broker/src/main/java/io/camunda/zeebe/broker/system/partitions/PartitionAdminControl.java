/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

public interface PartitionAdminControl {
  StreamProcessor getStreamProcessor();

  ZeebeDb getZeebeDb();

  LogStream getLogStream();

  /**
   * The live exporter director on this replica, or {@code null} if it isn't open yet -- needed to
   * read exporting-migration status directly from the running director (see {@link
   * ZeebePartitionAdminAccess#getExportingMigrationStatus()}).
   */
  @Nullable ExporterDirector getExporterDirector();

  void triggerSnapshot();

  boolean shouldProcess();

  void pauseProcessing() throws IOException;

  void resumeProcessing() throws IOException;

  /**
   * {@code true} once a snapshot capturing this replica's migrated state has been taken since it
   * last ran its migrations (see {@code MigrationSnapshotDirector}). Never resets to {@code false}
   * once set.
   */
  boolean isMigrationSnapshotTaken();
}
