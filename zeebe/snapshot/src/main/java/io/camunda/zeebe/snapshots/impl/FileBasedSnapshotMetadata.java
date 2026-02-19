/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
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
    @JsonProperty("exportedPosition") long minExportedPosition,
    long maxExportedPosition,
    long lastFollowupEventPosition,
    @JsonProperty("bootstrap") boolean isBootstrap)
    implements SnapshotMetadata {

  private static final ObjectMapper OBJECTMAPPER = new ObjectMapper();

  @JsonCreator
  public FileBasedSnapshotMetadata(
      @JsonProperty("version") final int version,
      @JsonProperty("processedPosition") final long processedPosition,
      @JsonProperty("exportedPosition") final long minExportedPosition,
      @JsonProperty("maxExportedPosition") final Long maxExportedPosition,
      @JsonProperty("lastFollowupEventPosition") final long lastFollowupEventPosition,
      @JsonProperty("bootstrap") final boolean isBootstrap) {
    this(
        version,
        processedPosition,
        minExportedPosition,
        // Backwards compatibility
        maxExportedPosition == null ? Long.MAX_VALUE : maxExportedPosition,
        lastFollowupEventPosition,
        isBootstrap);
  }

  public static FileBasedSnapshotMetadata forBootstrap(final int version) {
    return new FileBasedSnapshotMetadata(version, 0L, 0L, 0L, 0L, true);
  }

  public void encode(final OutputStream output) throws IOException {
    OBJECTMAPPER.writeValue(output, this);
  }

  public static FileBasedSnapshotMetadata decode(final byte[] serializedBytes) throws IOException {
    return new ObjectMapper().readValue(serializedBytes, FileBasedSnapshotMetadata.class);
  }
}
