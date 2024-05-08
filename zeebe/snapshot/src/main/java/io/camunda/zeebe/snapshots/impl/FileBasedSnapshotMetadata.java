/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.snapshots.SnapshotMetadata;
import java.io.IOException;
import java.io.OutputStream;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FileBasedSnapshotMetadata(
    int version, long processedPosition, long exportedPosition, long lastFollowupEventPosition)
    implements SnapshotMetadata {

  private static final ObjectMapper OBJECTMAPPER = new ObjectMapper();

  public void encode(final OutputStream output) throws IOException {
    OBJECTMAPPER.writeValue(output, this);
  }

  public static FileBasedSnapshotMetadata decode(final byte[] serializedBytes) throws IOException {
    return new ObjectMapper().readValue(serializedBytes, FileBasedSnapshotMetadata.class);
  }
}
