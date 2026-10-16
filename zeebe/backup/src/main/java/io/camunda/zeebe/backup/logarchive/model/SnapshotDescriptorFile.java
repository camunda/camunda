/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * The content of {@code state/<snapshotId>/descriptor.json} (spec section 9.5): the fields of
 * {@link ArchivedSnapshotJson} plus the snapshot's files.
 *
 * @param files file names of the snapshot, sorted; the constructor sorts them so that the order
 *     does not depend on the caller
 */
@JsonPropertyOrder({
  "id",
  "index",
  "term",
  "processedPosition",
  "exportedPosition",
  "lastFollowupEventPosition",
  "maxExportedPosition",
  "fileCount",
  "sizeBytes",
  "files"
})
public record SnapshotDescriptorFile(
    String id,
    long index,
    long term,
    long processedPosition,
    long exportedPosition,
    long lastFollowupEventPosition,
    long maxExportedPosition,
    int fileCount,
    long sizeBytes,
    List<String> files) {

  public SnapshotDescriptorFile {
    files = files == null ? List.of() : files.stream().sorted().toList();
  }
}
