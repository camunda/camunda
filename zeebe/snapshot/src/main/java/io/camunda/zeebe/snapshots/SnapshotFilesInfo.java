/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import java.util.Map;

/**
 * Per-file information about a snapshot's live files, as reported by a {@link
 * SnapshotFileInfoProvider}. Checksums are only present for files the provider can checksum,
 * whereas sizes are reported for every live file so callers can total a snapshot's size without
 * stat-ing each file.
 *
 * @param checksums fileName to CRC32C checksum
 * @param sizes fileName to size in bytes
 */
public record SnapshotFilesInfo(Map<String, Long> checksums, Map<String, Long> sizes) {

  private static final SnapshotFilesInfo NONE = new SnapshotFilesInfo(Map.of(), Map.of());

  /** Returns an empty instance, reporting neither checksums nor sizes for any file. */
  public static SnapshotFilesInfo none() {
    return NONE;
  }
}
