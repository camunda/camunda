/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs.manifest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.UnexpectedManifestState;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ManifestStateCastingTest {
  @Test
  public void shouldFailOnAsInProgress() {
    // given
    final var manifest =
        Manifest.createInProgress(
            new BackupImpl(
                new BackupIdentifierImpl(1, 2, 43),
                new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"),
                null,
                null));

    final var complete = manifest.complete();

    // when expect thrown
    assertThatThrownBy(complete::asInProgress)
        .isInstanceOf(UnexpectedManifestState.class)
        .hasMessageContaining("but was in 'COMPLETED'");
  }

  @Test
  public void shouldFailOnAsCompleted() {
    // given
    final var manifest =
        Manifest.createInProgress(
            new BackupImpl(
                new BackupIdentifierImpl(1, 2, 43),
                new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"),
                new NamedFileSetImpl(Map.of()),
                new NamedFileSetImpl(Map.of())));

    // when expect thrown
    assertThatThrownBy(manifest::asCompleted)
        .isInstanceOf(UnexpectedManifestState.class)
        .hasMessageContaining("but was in 'IN_PROGRESS'");
  }

  @Test
  public void shouldFailOnAsFailed() {
    // given
    final var manifest =
        Manifest.createInProgress(
            new BackupImpl(
                new BackupIdentifierImpl(1, 2, 43),
                new BackupDescriptorImpl(Optional.empty(), 2345234L, 3, "1.2.0-SNAPSHOT"),
                null,
                null));

    // when expect thrown
    assertThatThrownBy(manifest::asFailed)
        .isInstanceOf(UnexpectedManifestState.class)
        .hasMessageContaining("but was in 'IN_PROGRESS'");
  }
}
