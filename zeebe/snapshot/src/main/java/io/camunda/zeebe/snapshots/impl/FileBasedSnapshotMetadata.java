/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.snapshots.SnapshotMetadata;
import java.io.IOException;
import java.io.OutputStream;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FileBasedSnapshotMetadata(
    int version,
    long processedPosition,
    long exportedPosition,
    long lastFollowupEventPosition,
    @JsonProperty(defaultValue = "false") boolean isBootstrap)
    implements SnapshotMetadata {

  private static final ObjectMapper OBJECTMAPPER = new ObjectMapper();

  // Constructor for backward compatibility
  public FileBasedSnapshotMetadata(
      final int version,
      final long processedPosition,
      final long exportedPosition,
      final long lastFollowupEventPosition) {
    this(version, processedPosition, exportedPosition, lastFollowupEventPosition, false);
  }

  public static FileBasedSnapshotMetadata forBootstrap(final int version) {
    return new FileBasedSnapshotMetadata(version, 0L, 0L, 0L, true);
  }

  public void encode(final OutputStream output) throws IOException {
    OBJECTMAPPER.writeValue(output, this);
  }

  public static FileBasedSnapshotMetadata decode(final byte[] serializedBytes) throws IOException {
    return new ObjectMapper().readValue(serializedBytes, FileBasedSnapshotMetadata.class);
  }
}
