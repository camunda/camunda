/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import io.camunda.zeebe.eventplane.history.HistoryManifest;
import io.camunda.zeebe.eventplane.segment.LogId;

/**
 * A manifest that isn't the given log's part of a history: it belongs to another cluster or tenant,
 * or holds other partitions than the log's one.
 */
public abstract sealed class MismatchedManifestException extends IllegalArgumentException
    permits ManifestClusterMismatchException,
        ManifestTenantMismatchException,
        ManifestPartitionsMismatchException {

  private final LogId log;
  private final HistoryManifest manifest;

  MismatchedManifestException(
      final String message, final LogId log, final HistoryManifest manifest) {
    super(message);
    this.log = log;
    this.manifest = manifest;
  }

  /** The log that the manifest was given for. */
  public LogId log() {
    return log;
  }

  /** The manifest that doesn't fit the log. */
  public HistoryManifest manifest() {
    return manifest;
  }
}
