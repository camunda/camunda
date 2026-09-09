/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import static dev.hegel.Generators.composite;
import static dev.hegel.Generators.forType;
import static dev.hegel.Generators.fromRegex;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.longs;
import static dev.hegel.Generators.optional;
import static dev.hegel.Generators.sampledFrom;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.storage.Blob;
import dev.hegel.Generator;
import dev.hegel.HegelTest;
import dev.hegel.TestCase;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import org.mockito.Mockito;

final class ManifestMetadataPropertyTest {

  private static final String BASE_PATH = "base/";
  private static final String MANIFEST_BLOB_NAME = "manifest.json";

  private static final Generator<BackupIdentifierImpl> IDENTIFIERS =
      composite(
          tc ->
              new BackupIdentifierImpl(
                  tc.draw(integers().min(0).max(100)),
                  tc.draw(integers().min(1).max(64)),
                  tc.draw(longs().min(1).max(1_000_000L))));

  private static final Generator<Instant> CHECKPOINT_TIMESTAMPS =
      longs()
          .min(Instant.parse("2020-01-01T00:00:00Z").getEpochSecond())
          .max(Instant.parse("2030-01-01T00:00:00Z").getEpochSecond())
          .map(Instant::ofEpochSecond);

  private static final Generator<BackupDescriptorImpl> DESCRIPTORS =
      composite(
          tc ->
              new BackupDescriptorImpl(
                  tc.draw(optional(fromRegex("[a-zA-Z]{1,10}"))),
                  tc.draw(optional(longs().min(0).max(1_000_000L)))
                      .map(OptionalLong::of)
                      .orElse(OptionalLong.empty()),
                  tc.draw(longs().min(0).max(1_000_000L)),
                  tc.draw(integers().min(1).max(32)),
                  tc.draw(fromRegex("[a-zA-Z0-9]{1,20}")),
                  tc.draw(CHECKPOINT_TIMESTAMPS),
                  tc.draw(forType(CheckpointType.class))));

  private static final Generator<BackupImpl> BACKUPS =
      composite(
          tc ->
              new BackupImpl(
                  tc.draw(IDENTIFIERS),
                  tc.draw(DESCRIPTORS),
                  new NamedFileSetImpl(Map.of()),
                  new NamedFileSetImpl(Map.of())));

  private static final Generator<Manifest> MANIFESTS =
      composite(
          tc -> {
            final var inProgress = Manifest.createInProgress(tc.draw(BACKUPS));
            return switch (tc.draw(sampledFrom("IN_PROGRESS", "COMPLETED", "FAILED", "DELETED"))) {
              case "IN_PROGRESS" -> inProgress;
              case "COMPLETED" -> inProgress.complete();
              case "FAILED" -> inProgress.fail("test failure reason");
              case "DELETED" -> inProgress.complete().delete();
              default -> throw new IllegalStateException();
            };
          });

  @HegelTest(testCases = 100)
  void shouldRoundTripAnyManifest(final TestCase tc) {
    // given
    final var manifest = tc.draw(MANIFESTS, "manifest");
    final var expectedStatus = Manifest.toStatus(manifest);
    final var id = manifest.id();
    final var blobName =
        BASE_PATH
            + "manifests/"
            + id.partitionId()
            + "/"
            + id.checkpointId()
            + "/"
            + id.nodeId()
            + "/"
            + MANIFEST_BLOB_NAME;

    // when
    final var metadata = ManifestMetadata.fromManifest(manifest);
    final var blob = Mockito.mock(Blob.class);
    Mockito.when(blob.getName()).thenReturn(blobName);
    Mockito.when(blob.getMetadata()).thenReturn(metadata);
    final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

    // then
    assertThat(result).isPresent();
    final var status = result.get();
    assertThat(status.statusCode()).isEqualTo(expectedStatus.statusCode());
    assertThat(status.id().nodeId()).isEqualTo(id.nodeId());
    assertThat(status.id().partitionId()).isEqualTo(id.partitionId());
    assertThat(status.id().checkpointId()).isEqualTo(id.checkpointId());
    assertThat(status.failureReason()).isEqualTo(expectedStatus.failureReason());
    assertThat(status.created()).isEqualTo(expectedStatus.created());
    assertThat(status.lastModified()).isEqualTo(expectedStatus.lastModified());

    if (expectedStatus.descriptor().isPresent()) {
      assertThat(status.descriptor()).isPresent();
      final var desc = status.descriptor().get();
      final var expectedDesc = expectedStatus.descriptor().get();
      assertThat(desc.snapshotId()).isEqualTo(expectedDesc.snapshotId());
      assertThat(desc.firstLogPosition()).isEqualTo(expectedDesc.firstLogPosition());
      assertThat(desc.checkpointPosition()).isEqualTo(expectedDesc.checkpointPosition());
      assertThat(desc.numberOfPartitions()).isEqualTo(expectedDesc.numberOfPartitions());
      assertThat(desc.brokerVersion()).isEqualTo(expectedDesc.brokerVersion());
      assertThat(desc.checkpointTimestamp()).isEqualTo(expectedDesc.checkpointTimestamp());
      assertThat(desc.checkpointType()).isEqualTo(expectedDesc.checkpointType());
    } else {
      assertThat(status.descriptor()).isEmpty();
    }
  }
}
