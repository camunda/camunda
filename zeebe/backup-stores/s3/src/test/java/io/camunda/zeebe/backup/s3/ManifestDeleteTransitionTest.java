/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.s3.manifest.CompletedBackupManifest;
import io.camunda.zeebe.backup.s3.manifest.DeletedBackupManifest;
import io.camunda.zeebe.backup.s3.manifest.FailedBackupManifest;
import io.camunda.zeebe.backup.s3.manifest.FileSet;
import io.camunda.zeebe.backup.s3.manifest.InProgressBackupManifest;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class ManifestDeleteTransitionTest {

  private static final BackupIdentifierImpl BACKUP_ID = new BackupIdentifierImpl(1, 2, 3);
  private static final BackupDescriptorImpl DESCRIPTOR =
      new BackupDescriptorImpl(3L, 1, "8.7.0", Instant.now(), CheckpointType.MANUAL_BACKUP);
  private static final FileSet EMPTY_FILE_SET = FileSet.empty();

  @Nested
  class ManifestTransitions {

    @Test
    void shouldTransitionCompletedManifestToDeleted() {
      // given
      final var completedManifest =
          new CompletedBackupManifest(
              BACKUP_ID, DESCRIPTOR, EMPTY_FILE_SET, EMPTY_FILE_SET, Instant.now(), Instant.now());

      // when
      final var deletedManifest = completedManifest.asDeleted();

      // then
      assertThat(deletedManifest.statusCode()).isEqualTo(BackupStatusCode.DELETED);
      assertThat(deletedManifest.id()).isEqualTo(BACKUP_ID);
      assertThat(deletedManifest.descriptor()).contains(DESCRIPTOR);
      assertThat(deletedManifest.createdAt()).isEqualTo(completedManifest.createdAt());
      assertThat(deletedManifest.modifiedAt()).isAfterOrEqualTo(completedManifest.modifiedAt());
    }

    @Test
    void shouldTransitionFailedManifestToDeleted() {
      // given
      final var failedManifest =
          new FailedBackupManifest(
              BACKUP_ID,
              Optional.of(DESCRIPTOR),
              "failure reason",
              EMPTY_FILE_SET,
              EMPTY_FILE_SET,
              Instant.now(),
              Instant.now());

      // when
      final var deletedManifest = failedManifest.asDeleted();

      // then
      assertThat(deletedManifest.statusCode()).isEqualTo(BackupStatusCode.DELETED);
      assertThat(deletedManifest.id()).isEqualTo(BACKUP_ID);
      assertThat(deletedManifest.descriptor()).contains(DESCRIPTOR);
      assertThat(deletedManifest.createdAt()).isEqualTo(failedManifest.createdAt());
      assertThat(deletedManifest.modifiedAt()).isAfterOrEqualTo(failedManifest.modifiedAt());
    }

    @Test
    void shouldTransitionInProgressManifestToDeleted() {
      // given
      final var inProgressManifest =
          new InProgressBackupManifest(
              BACKUP_ID, DESCRIPTOR, EMPTY_FILE_SET, EMPTY_FILE_SET, Instant.now(), Instant.now());

      // when
      final var deletedManifest = inProgressManifest.asDeleted();

      // then
      assertThat(deletedManifest.statusCode()).isEqualTo(BackupStatusCode.DELETED);
      assertThat(deletedManifest.id()).isEqualTo(BACKUP_ID);
      assertThat(deletedManifest.descriptor()).contains(DESCRIPTOR);
      assertThat(deletedManifest.createdAt()).isEqualTo(inProgressManifest.createdAt());
      assertThat(deletedManifest.modifiedAt()).isAfterOrEqualTo(inProgressManifest.modifiedAt());
    }

    @Test
    void shouldReturnSameInstanceWhenAlreadyDeleted() {
      // given
      final var deletedManifest =
          new DeletedBackupManifest(
              BACKUP_ID,
              Optional.of(DESCRIPTOR),
              EMPTY_FILE_SET,
              EMPTY_FILE_SET,
              Instant.now(),
              Instant.now());

      // when
      final var result = deletedManifest.asDeleted();

      // then
      assertThat(result).isSameAs(deletedManifest);
    }
  }
}
