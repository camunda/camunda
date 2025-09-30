/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.BackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.common.Manifest.FailedManifest;
import io.camunda.zeebe.backup.common.Manifest.InProgressManifest;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ManifestManagerTest {

  @TempDir Path tempDir;
  private ManifestManager manifestManager;
  private BackupIdentifier backupIdentifier;
  private Backup backup;

  @BeforeEach
  void setUp() {
    manifestManager = new ManifestManager(tempDir);
    backupIdentifier = new BackupIdentifierImpl(1337, 0, 42L);
    backup = createBackup(backupIdentifier);
  }

  private InProgressManifest createInitialManifest() throws IOException {
    return manifestManager.createInitialManifest(backup);
  }

  @Test
  void shouldCreateInitialManifest() throws IOException {
    final var manifest = createInitialManifest();

    final var manifestPath = tempDir.resolve("0/42/1337/manifest.json");
    assertThat(Files.exists(manifestPath)).isTrue();
    assertThat(manifest.statusCode()).isEqualTo(Manifest.StatusCode.IN_PROGRESS);
  }

  @Test
  void shouldFailToCreateManifestIfAlreadyExists() throws IOException {
    final var manifestPath = tempDir.resolve("0/42/1337/manifest.json");
    Files.createDirectories(manifestPath.getParent());
    Files.write(manifestPath, "existing content".getBytes(), StandardOpenOption.CREATE_NEW);

    assertThatThrownBy(this::createInitialManifest)
        .isInstanceOf(UnexpectedManifestState.class)
        .hasMessageContaining("Manifest already exists.");
  }

  @Test
  void shouldFailToCreateManifestIfNotValidJson() throws IOException {
    final var manifestPath = tempDir.resolve("0/42/1337/manifest.json");
    Files.createDirectories(manifestPath.getParent());
    Files.write(manifestPath, "invalid json".getBytes(), StandardOpenOption.CREATE_NEW);

    assertThatThrownBy(() -> manifestManager.getManifest(backupIdentifier))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("Unable to read manifest from path");
  }

  @Test
  void shouldCompleteManifest() throws IOException {
    final var inProgressManifest = createInitialManifest();
    manifestManager.completeManifest(inProgressManifest);

    final var manifestPath = tempDir.resolve("0/42/1337/manifest.json");
    final var completedManifest = manifestManager.getManifest(backupIdentifier);
    assertThat(Files.exists(manifestPath)).isTrue();
    assertThat(completedManifest.statusCode()).isEqualTo(Manifest.StatusCode.COMPLETED);
  }

  @Test
  void shouldMarkManifestAsFailed() throws IOException {
    final var inProgressManifest = createInitialManifest();
    manifestManager.markAsFailed(backupIdentifier, "failure reason");

    final var failedManifest = manifestManager.getManifest(backupIdentifier);
    assertThat(failedManifest.statusCode()).isEqualTo(Manifest.StatusCode.FAILED);
    assertThat(((FailedManifest) failedManifest).failureReason()).isEqualTo("failure reason");
  }

  @Test
  void shouldDeleteManifest() throws IOException {
    final var inProgressManifest = createInitialManifest();
    manifestManager.completeManifest(inProgressManifest);

    manifestManager.deleteManifest(backupIdentifier);

    final var manifestPath = tempDir.resolve("0/42/1337/manifest.json");
    assertThat(Files.exists(manifestPath)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("provideWildcardsForListManifests")
  void shouldListManifests(final BackupIdentifierWildcardImpl wildcard, final int expectedSize)
      throws IOException {

    final List<BackupIdentifier> backupIdentifiers =
        List.of(
            new BackupIdentifierImpl(1337, 0, 42L),
            new BackupIdentifierImpl(1337, 1, 42L),
            new BackupIdentifierImpl(1337, 0, 43L),
            new BackupIdentifierImpl(1337, 1, 43L),
            new BackupIdentifierImpl(1338, 0, 42L),
            new BackupIdentifierImpl(1338, 0, 43L));
    for (final BackupIdentifier backupIdentifier : backupIdentifiers) {
      final var backup = createBackup(backupIdentifier);
      final var inProgressManifest = manifestManager.createInitialManifest(backup);
    }

    final Collection<Manifest> manifests = manifestManager.listManifests(wildcard);

    assertThat(manifests).hasSize(expectedSize);
  }

  private static Stream<Arguments> provideWildcardsForListManifests() {
    return Stream.of(
        Arguments.of(
            new BackupIdentifierWildcardImpl(
                Optional.empty(), Optional.empty(), CheckpointPattern.any()),
            6),
        Arguments.of(
            new BackupIdentifierWildcardImpl(
                Optional.of(1337), Optional.empty(), CheckpointPattern.any()),
            4),
        Arguments.of(
            new BackupIdentifierWildcardImpl(
                Optional.empty(), Optional.empty(), CheckpointPattern.of(42L)),
            3),
        Arguments.of(
            new BackupIdentifierWildcardImpl(
                Optional.of(1337), Optional.of(0), CheckpointPattern.of(42L)),
            1));
  }

  private static BackupImpl createBackup(final BackupIdentifier backupIdentifier) {
    return new BackupImpl(
        backupIdentifier,
        new BackupDescriptorImpl(
            Optional.of("snapshotId"), backupIdentifier.checkpointId(), 1, "8.7.0"),
        new NamedFileSetImpl(Map.of()),
        new NamedFileSetImpl(Map.of()));
  }
}
