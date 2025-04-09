/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.JsonData;

public class MetadataTest {
  final Metadata metadata =
      new Metadata().setBackupId(23L).setVersion("8.7.1").setPartCount(6).setPartNo(2);

  @Test
  public void shouldExtractMetadataFromSnapshotname() {
    final var name = metadata.buildSnapshotName();
    final var fromName = Metadata.extractMetadataFromSnapshotName(name);
    assertThat(metadata).isEqualTo(fromName);
    assertThat(Metadata.extractFromMetadataOrName(null, name)).isEqualTo(fromName);
  }

  @Test
  public void shouldExtractMetadataFromSnapshot() {
    final var jsonMap =
        Map.of(
            "backupId",
            JsonData.of(metadata.getBackupId()),
            "partNo",
            JsonData.of(metadata.getPartNo()),
            "partCount",
            JsonData.of(metadata.getPartCount()),
            "version",
            JsonData.of(metadata.getVersion()));
    assertThat(metadata).isEqualTo(Metadata.fromOSJsonData(jsonMap));
  }

  @Test
  public void shouldPreferExtractingFromMetadataField() {
    final var extracted = Metadata.extractFromMetadataOrName(metadata, "");
    assertThat(extracted).isEqualTo(metadata);
  }
}
