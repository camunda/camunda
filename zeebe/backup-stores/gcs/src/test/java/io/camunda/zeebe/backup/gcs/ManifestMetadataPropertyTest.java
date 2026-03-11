/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.storage.Blob;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.Mockito;

final class ManifestMetadataPropertyTest {

  private static final String BASE_PATH = "base/";
  private static final String MANIFEST_BLOB_NAME = "manifest.json";

  @Property(tries = 100)
  void shouldRoundTripAnyManifest(@ForAll("manifests") final Manifest manifest) {
    // given
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

  @Provide
  Arbitrary<Manifest> manifests() {
    return backups()
        .flatMap(
            backup -> {
              final var inProgress = Manifest.createInProgress(backup);
              return Arbitraries.of("IN_PROGRESS", "COMPLETED", "FAILED", "DELETED")
                  .map(
                      state ->
                          switch (state) {
                            case "IN_PROGRESS" -> inProgress;
                            case "COMPLETED" -> inProgress.complete();
                            case "FAILED" -> inProgress.fail("test failure reason");
                            case "DELETED" -> inProgress.complete().delete();
                            default -> throw new IllegalStateException();
                          });
            });
  }

  Arbitrary<BackupImpl> backups() {
    return Combinators.combine(identifiers(), descriptors())
        .as(
            (id, descriptor) ->
                new BackupImpl(
                    id,
                    descriptor,
                    new NamedFileSetImpl(Map.of()),
                    new NamedFileSetImpl(Map.of())));
  }

  Arbitrary<BackupIdentifierImpl> identifiers() {
    return Combinators.combine(
            Arbitraries.integers().between(0, 100),
            Arbitraries.integers().between(1, 64),
            Arbitraries.longs().between(1L, 1_000_000L))
        .as(BackupIdentifierImpl::new);
  }

  Arbitrary<BackupDescriptorImpl> descriptors() {
    return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).optional(),
            Arbitraries.longs().between(0L, 1_000_000L).optional(),
            Arbitraries.longs().between(0L, 1_000_000L),
            Arbitraries.integers().between(1, 32),
            Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20),
            Arbitraries.longs()
                .between(
                    Instant.parse("2020-01-01T00:00:00Z").getEpochSecond(),
                    Instant.parse("2030-01-01T00:00:00Z").getEpochSecond())
                .map(Instant::ofEpochSecond),
            Arbitraries.of(CheckpointType.values()))
        .as(
            (snapshotId, firstLogPos, checkpointPos, partitions, version, timestamp, type) ->
                new BackupDescriptorImpl(
                    snapshotId,
                    firstLogPos.map(OptionalLong::of).orElse(OptionalLong.empty()),
                    checkpointPos,
                    partitions,
                    version,
                    timestamp,
                    type));
  }
}
