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

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.BackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.common.Manifest.FailedManifest;
import io.camunda.zeebe.backup.common.Manifest.InProgressManifest;
import io.camunda.zeebe.backup.common.Manifest.StatusCode;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

    manifestManager.deleteManifest(inProgressManifest);

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

  /**
   * Deleting a backup removes its manifest and then the parent directories that removal left empty,
   * so a listing already walking the tree can find entries gone by the time it reaches them. The
   * store serves both calls concurrently, and nothing orders them against each other — a broker
   * deleting a superseded checkpoint while taking the next one does exactly this, and the listing
   * used to fail the backup being taken.
   */
  @Test
  void shouldListManifestsWhileOthersAreConcurrentlyDeleted() throws Exception {
    // given one manifest that stays, and many on the same partition that are deleted concurrently
    final var wildcard =
        new BackupIdentifierWildcardImpl(Optional.empty(), Optional.of(0), CheckpointPattern.any());
    manifestManager.createInitialManifest(createBackup(new BackupIdentifierImpl(1337, 0, 1L)));
    final var deletable = new ArrayList<Manifest>();
    for (long checkpointId = 2; checkpointId <= 400; checkpointId++) {
      deletable.add(
          manifestManager.createInitialManifest(
              createBackup(new BackupIdentifierImpl(1337, 0, checkpointId))));
    }

    // when listing for as long as the deletions run
    final var deleter = Executors.newSingleThreadExecutor();
    var listings = 0;
    try {
      final var deleting = deleter.submit(() -> deletable.forEach(manifestManager::deleteManifest));
      do {
        // then every listing succeeds and still reports the manifest nothing deleted
        assertThat(manifestManager.listManifests(wildcard))
            .extracting(manifest -> manifest.id().checkpointId())
            .contains(1L);
        listings++;
      } while (!deleting.isDone());
      // surfaces a failure of the deleting side, which the loop above would otherwise hide
      deleting.get(30, TimeUnit.SECONDS);
    } finally {
      deleter.shutdownNow();
    }

    // and the listings really did overlap the deletions
    assertThat(listings).isPositive();
  }

  private static Stream<Arguments> provideWildcardsForListManifests() {
    return Stream.of(
        Arguments.of(
            new BackupIdentifierWildcardImpl(
                Optional.empty(), Optional.empty(), CheckpointPattern.any()),
            6),
        Arguments.of(
            new BackupIdentifierWildcardImpl(
                Optional.of(BrokerMemberId.from(1337)), Optional.empty(), CheckpointPattern.any()),
            4),
        Arguments.of(
            new BackupIdentifierWildcardImpl(
                Optional.empty(), Optional.empty(), CheckpointPattern.of(42L)),
            3),
        Arguments.of(
            new BackupIdentifierWildcardImpl(
                Optional.of(BrokerMemberId.from(1337)), Optional.of(0), CheckpointPattern.of(42L)),
            1));
  }

  private static BackupImpl createBackup(final BackupIdentifier backupIdentifier) {
    return new BackupImpl(
        backupIdentifier,
        new BackupDescriptorImpl(
            backupIdentifier.checkpointId(),
            1,
            "8.7.0",
            Instant.now(),
            CheckpointType.MANUAL_BACKUP),
        new NamedFileSetImpl(Map.of()),
        new NamedFileSetImpl(Map.of()));
  }

  @Nested
  class ManifestCollectorTest {

    private final BackupIdentifierWildcard anyBackup =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), CheckpointPattern.any());

    @Test
    void shouldContinuePastAFileDeletedDuringTheWalk() throws IOException {
      // given
      final var collector = manifestManager.new ManifestCollector(anyBackup);
      final var vanishedManifest = tempDir.resolve("0/42/1337/manifest.json");

      // when
      final var result =
          collector.visitFileFailed(
              vanishedManifest, new NoSuchFileException(vanishedManifest.toString()));

      // then the walk carries on, having collected nothing for the entry that is no longer there
      assertThat(result).isEqualTo(FileVisitResult.CONTINUE);
      assertThat(collector.manifestFiles()).isEmpty();
    }

    @Test
    void shouldContinuePastADirectoryDeletedDuringTheWalk() throws IOException {
      // given
      final var collector = manifestManager.new ManifestCollector(anyBackup);
      final var vanishedCheckpointDir = tempDir.resolve("0/42");

      // when
      final var result =
          collector.postVisitDirectory(
              vanishedCheckpointDir, new NoSuchFileException(vanishedCheckpointDir.toString()));

      // then
      assertThat(result).isEqualTo(FileVisitResult.CONTINUE);
    }

    @Test
    void shouldContinueWhenADirectoryIsVisitedWithoutFailure() throws IOException {
      // given
      final var collector = manifestManager.new ManifestCollector(anyBackup);

      // when
      final var result = collector.postVisitDirectory(tempDir.resolve("0"), null);

      // then
      assertThat(result).isEqualTo(FileVisitResult.CONTINUE);
    }

    @Test
    void shouldFailWhenTheManifestsRootItselfIsMissing() {
      // given the failing path is the store's own root, not an entry below it
      final var collector = manifestManager.new ManifestCollector(anyBackup);

      // when listing the root itself as gone, then that is a broken store, not a concurrent
      // delete — every backup this tenant ever took would silently read back as zero
      assertThatThrownBy(
              () -> collector.visitFileFailed(tempDir, new NoSuchFileException(tempDir.toString())))
          .isInstanceOf(NoSuchFileException.class);
      assertThatThrownBy(
              () ->
                  collector.postVisitDirectory(
                      tempDir, new NoSuchFileException(tempDir.toString())))
          .isInstanceOf(NoSuchFileException.class);
    }

    @ParameterizedTest
    @MethodSource("provideNonRaceFailures")
    void shouldNotSwallowAFailureThatIsNotAConcurrentDelete(final IOException failure) {
      // given a failure that concurrent deletion never produces (permissions, a symlink loop, ...)
      final var collector = manifestManager.new ManifestCollector(anyBackup);
      final var path = tempDir.resolve("0/42/1337/manifest.json");

      // when — then it propagates rather than being read as "the entry is not there"
      assertThatThrownBy(() -> collector.visitFileFailed(path, failure)).isEqualTo(failure);
      assertThatThrownBy(() -> collector.postVisitDirectory(path, failure)).isEqualTo(failure);
    }

    private static Stream<IOException> provideNonRaceFailures() {
      return Stream.of(
          new AccessDeniedException("manifest.json"), new FileSystemLoopException("manifest.json"));
    }
  }

  @Nested
  class ManifestDeleteTransitionTest {
    @Test
    void shouldMarkInProgressManifestAsDeleted() throws IOException {
      // given
      final var inProgressManifest = createInitialManifest();

      // when
      manifestManager.markAsDeleted(inProgressManifest);

      // then
      final var manifest = manifestManager.getManifest(backupIdentifier);
      assertThat(manifest.statusCode()).isEqualTo(StatusCode.DELETED);
      assertThat(manifest.id()).isEqualTo(inProgressManifest.id());
    }

    @Test
    void shouldMarkCompletedManifestAsDeleted() throws IOException {
      // given
      final var inProgressManifest = createInitialManifest();
      manifestManager.completeManifest(inProgressManifest);
      final var completedManifest = manifestManager.getManifest(backupIdentifier);

      // when
      manifestManager.markAsDeleted(completedManifest);

      // then
      final var manifest = manifestManager.getManifest(backupIdentifier);
      assertThat(manifest.statusCode()).isEqualTo(StatusCode.DELETED);
      assertThat(manifest.id()).isEqualTo(inProgressManifest.id());
    }

    @Test
    void shouldMarkFailedManifestAsDeleted() throws IOException {
      // given
      final var inProgressManifest = createInitialManifest();
      manifestManager.markAsFailed(backupIdentifier, "failure reason");
      final var failedManifest = manifestManager.getManifest(backupIdentifier);

      // when
      manifestManager.markAsDeleted(failedManifest);

      // then
      final var manifest = manifestManager.getManifest(backupIdentifier);
      assertThat(manifest.statusCode()).isEqualTo(StatusCode.DELETED);
      assertThat(manifest.id()).isEqualTo(inProgressManifest.id());
    }

    @Test
    void shouldNotUpdateAlreadyDeletedManifest() throws IOException {
      // given
      final var inProgressManifest = createInitialManifest();
      manifestManager.completeManifest(inProgressManifest);
      final var completedManifest = manifestManager.getManifest(backupIdentifier);
      manifestManager.markAsDeleted(completedManifest);
      final var deletedManifest = manifestManager.getManifest(backupIdentifier);
      final var modifiedAt = deletedManifest.asDeleted().modifiedAt();

      // when
      manifestManager.markAsDeleted(deletedManifest);

      // then - manifest should remain deleted and unchanged
      final var manifest = manifestManager.getManifest(backupIdentifier);
      assertThat(manifest.statusCode()).isEqualTo(StatusCode.DELETED);
      assertThat(manifest.asDeleted().modifiedAt()).isEqualTo(modifiedAt);
    }
  }
}
