/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs.manifest;

import static io.camunda.zeebe.backup.common.Manifest.StatusCode.COMPLETED;
import static io.camunda.zeebe.backup.common.Manifest.StatusCode.FAILED;
import static io.camunda.zeebe.backup.common.Manifest.StatusCode.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.Manifest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ManifestStateTransitionTest {
  @Test
  void shouldStartInProgress() {
    // given

    // when
    final var manifest =
        Manifest.createInProgress(
            new BackupImpl(
                new BackupIdentifierImpl(1, 2, 43),
                new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"),
                null,
                null));

    // then
    assertThat(manifest.statusCode()).isEqualTo(IN_PROGRESS);
    assertThat(manifest.createdAt().getEpochSecond()).isPositive();
    assertThat(manifest.modifiedAt().getEpochSecond()).isPositive();
    assertThat(manifest.createdAt()).isEqualTo(manifest.modifiedAt());
  }

  @Test
  void shouldUpdateManifestToCompleted() {
    // given
    final var created =
        Manifest.createInProgress(
            new BackupImpl(
                new BackupIdentifierImpl(1, 2, 43),
                new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"),
                null,
                null));

    // when
    final var completed = created.complete();

    // then
    assertThat(completed.statusCode()).isEqualTo(COMPLETED);
    assertThat(completed.createdAt().getEpochSecond()).isPositive();
    assertThat(completed.modifiedAt().getEpochSecond()).isPositive();
    assertThat(completed.createdAt()).isBefore(completed.modifiedAt());
    assertThat(completed.createdAt()).isEqualTo(created.modifiedAt());
    assertThat(completed.modifiedAt()).isNotEqualTo(created.modifiedAt());
  }

  @Test
  void shouldUpdateManifestToFailed() {
    // given
    final var created =
        Manifest.createInProgress(
            new BackupImpl(
                new BackupIdentifierImpl(1, 2, 43),
                new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"),
                null,
                null));

    // when
    final var failed = created.fail("expected failure reason");

    // then
    assertThat(failed.statusCode()).isEqualTo(FAILED);
    assertThat(failed.createdAt().getEpochSecond()).isPositive();
    assertThat(failed.modifiedAt().getEpochSecond()).isPositive();
    assertThat(failed.createdAt()).isBefore(failed.modifiedAt());
    assertThat(failed.createdAt()).isEqualTo(created.modifiedAt());
    assertThat(failed.modifiedAt()).isNotEqualTo(created.modifiedAt());
    assertThat(failed.failureReason()).isEqualTo("expected failure reason");
  }

  @Test
  void shouldUpdateManifestFromCompletedToFailed() {
    // given
    final var created =
        Manifest.createInProgress(
            new BackupImpl(
                new BackupIdentifierImpl(1, 2, 43),
                new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"),
                null,
                null));

    final var completed = created.complete();

    // when
    final var failed = completed.fail("expected failure reason");

    // then
    assertThat(failed.statusCode()).isEqualTo(FAILED);
    assertThat(failed.createdAt().getEpochSecond()).isPositive();
    assertThat(failed.modifiedAt().getEpochSecond()).isPositive();
    assertThat(failed.createdAt()).isBefore(failed.modifiedAt());
    assertThat(failed.createdAt()).isEqualTo(created.modifiedAt());
    assertThat(failed.modifiedAt()).isNotEqualTo(created.modifiedAt());
    assertThat(failed.failureReason()).isEqualTo("expected failure reason");
  }
}
