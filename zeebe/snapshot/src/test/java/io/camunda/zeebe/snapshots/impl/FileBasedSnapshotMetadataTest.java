/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class FileBasedSnapshotMetadataTest {

  @Test
  public void shouldDeserializeMetadataFromPreviousVersion() throws IOException {
    final var previousMetadata =
        // language=JSON
        """
        {
          "version": 1,
          "processedPosition": 71662471,
          "exportedPosition": 74709149,
          "lastFollowupEventPosition": 74708149,
          "bootstrap": false
        }""";
    final var deserialized = FileBasedSnapshotMetadata.decode(previousMetadata.getBytes());
    assertThat(deserialized.isBootstrap()).isFalse();
    assertThat(deserialized.version()).isOne();
    assertThat(deserialized.minExportedPosition()).isEqualTo(74709149L);
    assertThat(deserialized.processedPosition()).isEqualTo(71662471L);
    assertThat(deserialized.lastFollowupEventPosition()).isEqualTo(74708149L);
    assertThat(deserialized.maxExportedPosition()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void shouldSerializeDeserialize() throws IOException {
    final var metadata = new FileBasedSnapshotMetadata(1, 100L, 200L, 300L, 350L, true);
    final var bos = new ByteArrayOutputStream(1024);
    metadata.encode(bos);
    final var deserialized = FileBasedSnapshotMetadata.decode(bos.toByteArray());
    assertThat(deserialized).isEqualTo(metadata);
  }
}
