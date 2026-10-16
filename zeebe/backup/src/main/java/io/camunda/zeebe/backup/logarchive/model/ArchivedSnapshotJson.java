/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * An archived snapshot as it appears in commit entries and roots. The descriptor stored next to the
 * snapshot files is {@link SnapshotDescriptorFile}, which repeats these fields.
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
  "sizeBytes"
})
public record ArchivedSnapshotJson(
    String id,
    long index,
    long term,
    long processedPosition,
    long exportedPosition,
    long lastFollowupEventPosition,
    long maxExportedPosition,
    int fileCount,
    long sizeBytes) {}
