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
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class ManifestMetadataTest {

  private static final String BASE_PATH = "my-prefix/";
  private static final String MANIFEST_BLOB_NAME = "manifest.json";

  private static BackupImpl createBackup(
      final int nodeId, final int partitionId, final long checkpointId) {
    return new BackupImpl(
        new BackupIdentifierImpl(nodeId, partitionId, checkpointId),
        new BackupDescriptorImpl(
            Optional.of("snap-1"),
            OptionalLong.of(100L),
            500L,
            3,
            "8.5.0",
            Instant.parse("2025-01-15T10:30:00Z"),
            CheckpointType.MANUAL_BACKUP),
        new NamedFileSetImpl(Map.of("file1", Path.of("f1"))),
        new NamedFileSetImpl(Map.of("seg1", Path.of("s1"))));
  }

  private static BackupImpl createBackupWithMinimalDescriptor(
      final int nodeId, final int partitionId, final long checkpointId) {
    return new BackupImpl(
        new BackupIdentifierImpl(nodeId, partitionId, checkpointId),
        new BackupDescriptorImpl(
            Optional.empty(), OptionalLong.empty(), 500L, 3, "8.5.0", null, null),
        new NamedFileSetImpl(Map.of()),
        new NamedFileSetImpl(Map.of()));
  }

  private static String blobPath(final int partitionId, final long checkpointId, final int nodeId) {
    return BASE_PATH
        + "manifests/"
        + partitionId
        + "/"
        + checkpointId
        + "/"
        + nodeId
        + "/"
        + MANIFEST_BLOB_NAME;
  }

  private static Blob mockBlob(final String name, final Map<String, String> metadata) {
    final var blob = Mockito.mock(Blob.class);
    Mockito.when(blob.getName()).thenReturn(name);
    Mockito.when(blob.getMetadata()).thenReturn(metadata);
    return blob;
  }

  @Nested
  class FromManifest {

    @Test
    void shouldIncludeStatusCodeForInProgressManifest() {
      // given
      final var manifest = Manifest.createInProgress(createBackup(1, 2, 3));

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);

      // then
      assertThat(metadata).containsEntry(ManifestMetadata.STATUS_CODE, "IN_PROGRESS");
    }

    @Test
    void shouldIncludeStatusCodeForCompletedManifest() {
      // given
      final var manifest = Manifest.createInProgress(createBackup(1, 2, 3)).complete();

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);

      // then
      assertThat(metadata).containsEntry(ManifestMetadata.STATUS_CODE, "COMPLETED");
    }

    @Test
    void shouldIncludeFailureReasonForFailedManifest() {
      // given
      final var manifest = Manifest.createInProgress(createBackup(1, 2, 3)).fail("disk full");

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);

      // then
      assertThat(metadata)
          .containsEntry(ManifestMetadata.STATUS_CODE, "FAILED")
          .containsEntry(ManifestMetadata.FAILURE_REASON, "disk full");
    }

    @Test
    void shouldIncludeStatusCodeForDeletedManifest() {
      // given
      final var manifest = Manifest.createInProgress(createBackup(1, 2, 3)).complete().delete();

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);

      // then
      assertThat(metadata).containsEntry(ManifestMetadata.STATUS_CODE, "DELETED");
    }

    @Test
    void shouldIncludeAllDescriptorFields() {
      // given
      final var manifest = Manifest.createInProgress(createBackup(1, 2, 3));

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);

      // then
      assertThat(metadata)
          .containsEntry(ManifestMetadata.SNAPSHOT_ID, "snap-1")
          .containsEntry(ManifestMetadata.FIRST_LOG_POSITION, "100")
          .containsEntry(ManifestMetadata.CHECKPOINT_POSITION, "500")
          .containsEntry(ManifestMetadata.NUMBER_OF_PARTITIONS, "3")
          .containsEntry(ManifestMetadata.BROKER_VERSION, "8.5.0")
          .containsEntry(ManifestMetadata.CHECKPOINT_TIMESTAMP, "2025-01-15T10:30:00Z")
          .containsEntry(ManifestMetadata.CHECKPOINT_TYPE, "MANUAL_BACKUP");
    }

    @Test
    void shouldOmitOptionalDescriptorFieldsWhenAbsent() {
      // given
      final var manifest = Manifest.createInProgress(createBackupWithMinimalDescriptor(1, 2, 3));

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);

      // then
      assertThat(metadata)
          .doesNotContainKey(ManifestMetadata.SNAPSHOT_ID)
          .doesNotContainKey(ManifestMetadata.FIRST_LOG_POSITION)
          .doesNotContainKey(ManifestMetadata.CHECKPOINT_TIMESTAMP)
          .doesNotContainKey(ManifestMetadata.CHECKPOINT_TYPE)
          .containsEntry(ManifestMetadata.CHECKPOINT_POSITION, "500")
          .containsEntry(ManifestMetadata.NUMBER_OF_PARTITIONS, "3");
    }

    @Test
    void shouldIncludeCreatedAndModifiedTimestamps() {
      // given
      final var manifest = Manifest.createInProgress(createBackup(1, 2, 3));

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);

      // then
      assertThat(metadata)
          .containsKey(ManifestMetadata.CREATED_AT)
          .containsKey(ManifestMetadata.MODIFIED_AT);
      assertThat(Instant.parse(metadata.get(ManifestMetadata.CREATED_AT))).isNotNull();
      assertThat(Instant.parse(metadata.get(ManifestMetadata.MODIFIED_AT))).isNotNull();
    }

    @Test
    void shouldNotIncludeFailureReasonForNonFailedManifest() {
      // given
      final var manifest = Manifest.createInProgress(createBackup(1, 2, 3)).complete();

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);

      // then
      assertThat(metadata).doesNotContainKey(ManifestMetadata.FAILURE_REASON);
    }

    @Test
    void shouldOmitDescriptorFieldsWhenDescriptorIsNull() {
      // given
      final var manifest = Manifest.createFailed(new BackupIdentifierImpl(1, 2, 3));

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);

      // then
      assertThat(metadata)
          .containsEntry(ManifestMetadata.STATUS_CODE, "FAILED")
          .doesNotContainKey(ManifestMetadata.CHECKPOINT_POSITION)
          .doesNotContainKey(ManifestMetadata.BROKER_VERSION);
    }
  }

  @Nested
  class ToBackupStatus {

    @Test
    void shouldReturnEmptyWhenMetadataIsNull() {
      // given
      final var blob = mockBlob(blobPath(2, 3, 1), null);

      // when
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenStatusCodeIsMissing() {
      // given
      final var blob = mockBlob(blobPath(2, 3, 1), Map.of("some-key", "some-value"));

      // when
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldParseCompletedStatusAndIdentifier() {
      // given
      final var metadata =
          Map.of(
              ManifestMetadata.STATUS_CODE, "COMPLETED",
              ManifestMetadata.CHECKPOINT_POSITION, "500",
              ManifestMetadata.NUMBER_OF_PARTITIONS, "3",
              ManifestMetadata.BROKER_VERSION, "8.5.0");
      final var blob = mockBlob(blobPath(2, 3, 1), metadata);

      // when
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      final var status = result.get();
      assertThat(status.statusCode()).isEqualTo(BackupStatusCode.COMPLETED);
      assertThat(status.id().partitionId()).isEqualTo(2);
      assertThat(status.id().checkpointId()).isEqualTo(3L);
      assertThat(status.id().nodeId()).isEqualTo(1);
    }

    @Test
    void shouldParseFailedStatusWithReason() {
      // given
      final var metadata =
          Map.of(
              ManifestMetadata.STATUS_CODE, "FAILED",
              ManifestMetadata.FAILURE_REASON, "disk full");
      final var blob = mockBlob(blobPath(2, 3, 1), metadata);

      // when
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      final var status = result.get();
      assertThat(status.statusCode()).isEqualTo(BackupStatusCode.FAILED);
      assertThat(status.failureReason()).isEqualTo(Optional.of("disk full"));
    }

    @Test
    void shouldParseCreatedAndModifiedTimestamps() {
      // given
      final var createdAt = Instant.parse("2025-01-15T10:00:00Z");
      final var modifiedAt = Instant.parse("2025-01-15T11:00:00Z");
      final var metadata =
          Map.of(
              ManifestMetadata.STATUS_CODE, "COMPLETED",
              ManifestMetadata.CREATED_AT, createdAt.toString(),
              ManifestMetadata.MODIFIED_AT, modifiedAt.toString());
      final var blob = mockBlob(blobPath(2, 3, 1), metadata);

      // when
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().created()).isEqualTo(Optional.of(createdAt));
      assertThat(result.get().lastModified()).isEqualTo(Optional.of(modifiedAt));
    }

    @Test
    void shouldReturnEmptyDescriptorWhenCheckpointPositionIsMissing() {
      // given
      final var metadata = Map.of(ManifestMetadata.STATUS_CODE, "IN_PROGRESS");
      final var blob = mockBlob(blobPath(2, 3, 1), metadata);

      // when
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().descriptor()).isEmpty();
    }

    @Test
    void shouldParseFullDescriptor() {
      // given
      final var metadata =
          Map.of(
              ManifestMetadata.STATUS_CODE, "COMPLETED",
              ManifestMetadata.SNAPSHOT_ID, "snap-1",
              ManifestMetadata.FIRST_LOG_POSITION, "100",
              ManifestMetadata.CHECKPOINT_POSITION, "500",
              ManifestMetadata.NUMBER_OF_PARTITIONS, "3",
              ManifestMetadata.BROKER_VERSION, "8.5.0",
              ManifestMetadata.CHECKPOINT_TIMESTAMP, "2025-01-15T10:30:00Z",
              ManifestMetadata.CHECKPOINT_TYPE, "MANUAL_BACKUP");
      final var blob = mockBlob(blobPath(2, 3, 1), metadata);

      // when
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      final var descriptor = result.get().descriptor();
      assertThat(descriptor).isPresent();
      assertThat(descriptor.get().snapshotId()).isEqualTo(Optional.of("snap-1"));
      assertThat(descriptor.get().firstLogPosition()).isEqualTo(OptionalLong.of(100L));
      assertThat(descriptor.get().checkpointPosition()).isEqualTo(500L);
      assertThat(descriptor.get().numberOfPartitions()).isEqualTo(3);
      assertThat(descriptor.get().brokerVersion()).isEqualTo("8.5.0");
      assertThat(descriptor.get().checkpointTimestamp())
          .isEqualTo(Instant.parse("2025-01-15T10:30:00Z"));
      assertThat(descriptor.get().checkpointType()).isEqualTo(CheckpointType.MANUAL_BACKUP);
    }

    @Test
    void shouldDefaultCheckpointTypeToManualBackupWhenMissing() {
      // given
      final var metadata =
          Map.of(
              ManifestMetadata.STATUS_CODE, "COMPLETED",
              ManifestMetadata.CHECKPOINT_POSITION, "500",
              ManifestMetadata.NUMBER_OF_PARTITIONS, "3",
              ManifestMetadata.BROKER_VERSION, "8.5.0");
      final var blob = mockBlob(blobPath(2, 3, 1), metadata);

      // when
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().descriptor()).isPresent();
      assertThat(result.get().descriptor().get().checkpointType())
          .isEqualTo(CheckpointType.MANUAL_BACKUP);
    }
  }

  @Nested
  class PathParsing {

    @Test
    void shouldParseIdentifierFromPathWithBasePath() {
      // given
      final var metadata = Map.of(ManifestMetadata.STATUS_CODE, "COMPLETED");
      final var blob = mockBlob("my-prefix/manifests/4/1000/2/manifest.json", metadata);

      // when
      final var result = ManifestMetadata.toBackupStatus(blob, "my-prefix/", MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().id().partitionId()).isEqualTo(4);
      assertThat(result.get().id().checkpointId()).isEqualTo(1000L);
      assertThat(result.get().id().nodeId()).isEqualTo(2);
    }

    @Test
    void shouldParseIdentifierFromPathWithoutBasePath() {
      // given
      final var metadata = Map.of(ManifestMetadata.STATUS_CODE, "COMPLETED");
      final var blob = mockBlob("manifests/1/99/0/manifest.json", metadata);

      // when
      final var result = ManifestMetadata.toBackupStatus(blob, "", MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().id().partitionId()).isEqualTo(1);
      assertThat(result.get().id().checkpointId()).isEqualTo(99L);
      assertThat(result.get().id().nodeId()).isEqualTo(0);
    }

    @Test
    void shouldParseIdentifierWithLargeValues() {
      // given
      final var metadata = Map.of(ManifestMetadata.STATUS_CODE, "IN_PROGRESS");
      final var blob = mockBlob(BASE_PATH + "manifests/100/9999999999/50/manifest.json", metadata);

      // when
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().id().partitionId()).isEqualTo(100);
      assertThat(result.get().id().checkpointId()).isEqualTo(9999999999L);
      assertThat(result.get().id().nodeId()).isEqualTo(50);
    }
  }

  @Nested
  class RoundTrip {

    @Test
    void shouldRoundTripCompletedManifest() {
      // given
      final var manifest = Manifest.createInProgress(createBackup(1, 2, 3)).complete();
      final var expectedStatus = Manifest.toStatus(manifest);

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);
      final var blob = mockBlob(blobPath(2, 3, 1), metadata);
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      final var status = result.get();
      assertThat(status.statusCode()).isEqualTo(expectedStatus.statusCode());
      assertThat(status.id().nodeId()).isEqualTo(expectedStatus.id().nodeId());
      assertThat(status.id().partitionId()).isEqualTo(expectedStatus.id().partitionId());
      assertThat(status.id().checkpointId()).isEqualTo(expectedStatus.id().checkpointId());
      assertThat(status.failureReason()).isEqualTo(expectedStatus.failureReason());
      assertThat(status.created()).isEqualTo(expectedStatus.created());
      assertThat(status.lastModified()).isEqualTo(expectedStatus.lastModified());
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
    }

    @Test
    void shouldRoundTripInProgressManifest() {
      // given
      final var manifest = Manifest.createInProgress(createBackup(5, 10, 42));
      final var expectedStatus = Manifest.toStatus(manifest);

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);
      final var blob = mockBlob(blobPath(10, 42, 5), metadata);
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      final var status = result.get();
      assertThat(status.statusCode()).isEqualTo(BackupStatusCode.IN_PROGRESS);
      assertThat(status.id().nodeId()).isEqualTo(expectedStatus.id().nodeId());
      assertThat(status.id().partitionId()).isEqualTo(expectedStatus.id().partitionId());
      assertThat(status.id().checkpointId()).isEqualTo(expectedStatus.id().checkpointId());
      assertThat(status.descriptor()).isPresent();
    }

    @Test
    void shouldRoundTripFailedManifestWithReason() {
      // given
      final var manifest = Manifest.createInProgress(createBackup(0, 1, 7)).fail("out of space");
      final var expectedStatus = Manifest.toStatus(manifest);

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);
      final var blob = mockBlob(blobPath(1, 7, 0), metadata);
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      final var status = result.get();
      assertThat(status.statusCode()).isEqualTo(BackupStatusCode.FAILED);
      assertThat(status.failureReason()).isEqualTo(Optional.of("out of space"));
      assertThat(status.created()).isEqualTo(expectedStatus.created());
      assertThat(status.lastModified()).isEqualTo(expectedStatus.lastModified());
    }

    @Test
    void shouldRoundTripDeletedManifest() {
      // given
      final var manifest = Manifest.createInProgress(createBackup(2, 3, 99)).complete().delete();

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);
      final var blob = mockBlob(blobPath(3, 99, 2), metadata);
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().statusCode()).isEqualTo(BackupStatusCode.DELETED);
    }

    @Test
    void shouldRoundTripManifestWithMinimalDescriptor() {
      // given
      final var manifest =
          Manifest.createInProgress(createBackupWithMinimalDescriptor(1, 2, 3)).complete();

      // when
      final var metadata = ManifestMetadata.fromManifest(manifest);
      final var blob = mockBlob(blobPath(2, 3, 1), metadata);
      final var result = ManifestMetadata.toBackupStatus(blob, BASE_PATH, MANIFEST_BLOB_NAME);

      // then
      assertThat(result).isPresent();
      final var descriptor = result.get().descriptor();
      assertThat(descriptor).isPresent();
      assertThat(descriptor.get().snapshotId()).isEmpty();
      assertThat(descriptor.get().firstLogPosition()).isEmpty();
      assertThat(descriptor.get().checkpointPosition()).isEqualTo(500L);
      assertThat(descriptor.get().checkpointTimestamp()).isNull();
      assertThat(descriptor.get().checkpointType()).isEqualTo(CheckpointType.MANUAL_BACKUP);
    }
  }
}
