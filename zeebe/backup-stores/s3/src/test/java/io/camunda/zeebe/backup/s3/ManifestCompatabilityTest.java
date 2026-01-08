/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static io.camunda.zeebe.backup.s3.S3BackupStore.MAPPER;

import io.camunda.zeebe.backup.s3.manifest.CompletedBackupManifest;
import io.camunda.zeebe.backup.s3.manifest.FailedBackupManifest;
import io.camunda.zeebe.backup.s3.manifest.InProgressBackupManifest;
import io.camunda.zeebe.backup.s3.manifest.ValidBackupManifest;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class ManifestCompatabilityTest {
  @Test
  void shouldParseCompletedManifestFromPreviousVersion() throws IOException {
    // given
    final var objectReader = MAPPER.readerFor(ValidBackupManifest.class);

    // when
    final var manifest =
        objectReader.readValue(
            getClass().getResourceAsStream("/manifests/8.1/completed.json"),
            ValidBackupManifest.class);

    // then
    Assertions.assertThat(manifest).isInstanceOf(CompletedBackupManifest.class);
    final var completed = (CompletedBackupManifest) manifest;
    Assertions.assertThat(completed.segmentFiles()).isNotNull();
    Assertions.assertThat(completed.snapshotFiles()).isNotNull();
    Assertions.assertThat(completed.segmentFiles().files()).isNotEmpty();
    Assertions.assertThat(completed.snapshotFiles().files()).isNotEmpty();
  }

  @Test
  void shouldParseCompletedManifestWithoutFilesFromPreviousVersion() throws IOException {
    // given
    final var objectReader = MAPPER.readerFor(ValidBackupManifest.class);

    // when
    final var manifest =
        objectReader.readValue(
            getClass().getResourceAsStream("/manifests/8.1/completed-empty.json"),
            CompletedBackupManifest.class);

    // then
    Assertions.assertThat(manifest.segmentFiles()).isNotNull();
    Assertions.assertThat(manifest.snapshotFiles()).isNotNull();
    Assertions.assertThat(manifest.segmentFiles().files()).isEmpty();
    Assertions.assertThat(manifest.snapshotFiles().files()).isEmpty();
  }

  @Test
  void shouldParseInProgressManifestFromPreviousVersion() throws IOException {
    // given
    final var objectReader = MAPPER.readerFor(ValidBackupManifest.class);

    // when
    final var manifest =
        objectReader.readValue(
            getClass().getResourceAsStream("/manifests/8.1/in-progress.json"),
            InProgressBackupManifest.class);

    // then
    Assertions.assertThat(manifest.segmentFiles()).isNotNull();
    Assertions.assertThat(manifest.snapshotFiles()).isNotNull();
    Assertions.assertThat(manifest.segmentFiles().files()).isNotEmpty();
    Assertions.assertThat(manifest.snapshotFiles().files()).isNotEmpty();
  }

  @Test
  void shouldParseFailedManifestFromPreviousVersion() throws IOException {
    // given
    final var objectReader = MAPPER.readerFor(ValidBackupManifest.class);

    // when
    final var manifest =
        objectReader.readValue(
            getClass().getResourceAsStream("/manifests/8.1/failed.json"),
            FailedBackupManifest.class);

    // then
    Assertions.assertThat(manifest.segmentFiles()).isNotNull();
    Assertions.assertThat(manifest.snapshotFiles()).isNotNull();
    Assertions.assertThat(manifest.segmentFiles().files()).isNotEmpty();
    Assertions.assertThat(manifest.snapshotFiles().files()).isNotEmpty();
  }

  @Test
  void shouldParseManifestWithBigDecimalDateFormat() throws IOException {
    // given
    // Date format: epoch.nanoseconds (1668519689.290560717 = 2022-11-15T13:41:29.290560717Z)
    final var json =
        """
        {
           "statusCode": "completed",
           "id": { "nodeId": 0, "partitionId": 1, "checkpointId": 1668519689 },
           "descriptor": { "checkpointPosition": 2641349, "numberOfPartitions": 3, "brokerVersion": "8.9.0-SNAPSHOT" },
           "createdAt":"1668519689.290560717",
           "modifiedAt":"1668519689.290560717"
         }
        """;

    // when
    final var manifest = MAPPER.readValue(json, CompletedBackupManifest.class);

    // then
    Assertions.assertThat(manifest.createdAt()).isNotNull();
    final var createdAtUtc = manifest.createdAt().atZone(java.time.ZoneOffset.UTC);
    Assertions.assertThat(createdAtUtc.getYear()).isEqualTo(2022);
    Assertions.assertThat(createdAtUtc.getMonthValue()).isEqualTo(11);
    Assertions.assertThat(createdAtUtc.getDayOfMonth()).isEqualTo(15);
    Assertions.assertThat(createdAtUtc.getHour()).isEqualTo(13);
    Assertions.assertThat(createdAtUtc.getMinute()).isEqualTo(41);
    Assertions.assertThat(createdAtUtc.getSecond()).isEqualTo(29);
    Assertions.assertThat(manifest.createdAt().getNano()).isEqualTo(290560717);

    Assertions.assertThat(manifest.modifiedAt()).isNotNull();
    final var modifiedAtUtc = manifest.modifiedAt().atZone(java.time.ZoneOffset.UTC);
    Assertions.assertThat(modifiedAtUtc.getYear()).isEqualTo(2022);
    Assertions.assertThat(modifiedAtUtc.getMonthValue()).isEqualTo(11);
    Assertions.assertThat(modifiedAtUtc.getDayOfMonth()).isEqualTo(15);
    Assertions.assertThat(modifiedAtUtc.getHour()).isEqualTo(13);
    Assertions.assertThat(modifiedAtUtc.getMinute()).isEqualTo(41);
    Assertions.assertThat(modifiedAtUtc.getSecond()).isEqualTo(29);
    Assertions.assertThat(manifest.modifiedAt().getNano()).isEqualTo(290560717);
  }

  @Test
  void shouldParseManifestWithRFCDateFormat() throws IOException {
    // given
    final var json =
        """
        {
           "statusCode": "completed",
           "id": { "nodeId": 0, "partitionId": 1, "checkpointId": 1668519689 },
           "descriptor": { "checkpointPosition": 2641349, "numberOfPartitions": 3, "brokerVersion": "8.9.0-SNAPSHOT" },
           "createdAt":"2025-12-22T20:12:33.641444035Z",
           "modifiedAt":"2025-12-22T20:12:33.641444035Z"
         }
        """;

    // when
    final var manifest = MAPPER.readValue(json, CompletedBackupManifest.class);

    // then
    Assertions.assertThat(manifest.createdAt()).isNotNull();
    final var createdAtUtc = manifest.createdAt().atZone(java.time.ZoneOffset.UTC);
    Assertions.assertThat(createdAtUtc.getYear()).isEqualTo(2025);
    Assertions.assertThat(createdAtUtc.getMonthValue()).isEqualTo(12);
    Assertions.assertThat(createdAtUtc.getDayOfMonth()).isEqualTo(22);
    Assertions.assertThat(createdAtUtc.getHour()).isEqualTo(20);
    Assertions.assertThat(createdAtUtc.getMinute()).isEqualTo(12);
    Assertions.assertThat(createdAtUtc.getSecond()).isEqualTo(33);
    Assertions.assertThat(manifest.createdAt().getNano()).isEqualTo(641444035);

    Assertions.assertThat(manifest.modifiedAt()).isNotNull();
    final var modifiedAtUtc = manifest.modifiedAt().atZone(java.time.ZoneOffset.UTC);
    Assertions.assertThat(modifiedAtUtc.getYear()).isEqualTo(2025);
    Assertions.assertThat(modifiedAtUtc.getMonthValue()).isEqualTo(12);
    Assertions.assertThat(modifiedAtUtc.getDayOfMonth()).isEqualTo(22);
    Assertions.assertThat(modifiedAtUtc.getHour()).isEqualTo(20);
    Assertions.assertThat(modifiedAtUtc.getMinute()).isEqualTo(12);
    Assertions.assertThat(modifiedAtUtc.getSecond()).isEqualTo(33);
    Assertions.assertThat(manifest.modifiedAt().getNano()).isEqualTo(641444035);
  }
}
