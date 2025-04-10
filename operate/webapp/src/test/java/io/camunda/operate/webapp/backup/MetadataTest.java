/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MetadataTest {
  @Test
  public void shouldExtractMetadataFromSnapshotname() {
    final Metadata metadata =
        new Metadata().setBackupId(23L).setVersion("8.7.1").setPartCount(6).setPartNo(2);
    final var name = metadata.buildSnapshotName();
    final var fromName = Metadata.extractMetadataFromSnapshotName(name);
    assertThat(metadata).isEqualTo(fromName);
    assertThat(Metadata.extractFromMetadataOrName(new ObjectMapper(), Map.of(), name))
        .isEqualTo(fromName);
  }

  @Test
  public void shouldPreferExtractingFromMetadataField() {
    final Metadata metadata =
        new Metadata().setBackupId(23L).setVersion("8.7.1").setPartCount(6).setPartNo(2);
    final var extracted =
        Metadata.extractFromMetadataOrName(
            new ObjectMapper(),
            Map.of(
                "backupId",
                metadata.getBackupId(),
                "partNo",
                metadata.getPartNo(),
                "partCount",
                metadata.getPartCount(),
                "version",
                metadata.getVersion()),
            "");
    assertThat(extracted).isEqualTo(metadata);
  }
}
