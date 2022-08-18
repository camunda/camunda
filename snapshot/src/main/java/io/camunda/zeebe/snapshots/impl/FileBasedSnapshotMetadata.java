/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.snapshots.SnapshotMetadata;
import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FileBasedSnapshotMetadata(
    int version, long processedPosition, long exportedPosition, long lastFollowupEventPosition)
    implements SnapshotMetadata {

  public byte[] encode() throws JsonProcessingException {
    return new ObjectMapper().writeValueAsBytes(this);
  }

  public static FileBasedSnapshotMetadata decode(final byte[] serializedBytes) throws IOException {
    return new ObjectMapper().readValue(serializedBytes, FileBasedSnapshotMetadata.class);
  }
}
