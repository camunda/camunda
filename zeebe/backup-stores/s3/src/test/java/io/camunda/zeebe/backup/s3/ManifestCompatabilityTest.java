/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

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
    final var objectReader = S3BackupStore.MAPPER.readerFor(ValidBackupManifest.class);

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
    final var objectReader = S3BackupStore.MAPPER.readerFor(ValidBackupManifest.class);

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
    final var objectReader = S3BackupStore.MAPPER.readerFor(ValidBackupManifest.class);

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
    final var objectReader = S3BackupStore.MAPPER.readerFor(ValidBackupManifest.class);

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
}
