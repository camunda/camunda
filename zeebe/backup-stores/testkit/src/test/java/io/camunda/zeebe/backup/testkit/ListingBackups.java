/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import io.camunda.zeebe.backup.testkit.support.WildcardBackupProvider;
import io.camunda.zeebe.backup.testkit.support.WildcardBackupProvider.WildcardTestParameter;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

public interface ListingBackups {

  BackupStore getStore();

  @Test
  default void canListNoBackupsWhenStoreIsEmpty() {
    // when
    final var status =
        getStore()
            .list(
                new BackupIdentifierWildcardImpl(
                    Optional.empty(), Optional.of(1), CheckpointPattern.of(1)));

    // then
    assertThat(status).succeedsWithin(Duration.ofSeconds(5));
    final var result = status.join();
    assertThat(result).isEmpty();
  }

  @ParameterizedTest
  @ArgumentsSource(WildcardBackupProvider.class)
  default void canFindBackupByWildcard(final WildcardTestParameter parameter) {
    // given

    final var backups =
        Stream.concat(parameter.unexpectedIds().stream(), parameter.expectedIds().stream())
            .map(
                id -> {
                  try {
                    return getBackup(id);
                  } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                  }
                });
    backups.map(backup -> getStore().save(backup)).forEach(CompletableFuture::join);

    // when
    final var status = getStore().list(parameter.wildcard());

    assertThat(status).succeedsWithin(Duration.ofSeconds(20));
    final var result = status.join();
    assertThat(result)
        .map(BackupStatus::id)
        .containsExactlyInAnyOrderElementsOf(parameter.expectedIds());
  }

  default Backup getBackup(final BackupIdentifierImpl id) throws IOException {
    return TestBackupProvider.minimalBackupWithId(id);
  }

  @Test
  default void shouldFilterBackupsInTimeRange() throws IOException {
    // given
    final var from = Instant.parse("2024-01-01T00:00:00Z");
    final var to = Instant.parse("2024-01-01T12:00:00Z");
    final var generator = new CheckpointIdGenerator(0L);

    final var timestampInRange = Instant.parse("2024-01-01T06:00:00Z");
    final var timestampBeforeRange = Instant.parse("2023-12-31T23:59:59Z");
    final var timestampAfterRange = Instant.parse("2024-01-01T12:00:01Z");

    final var backupInRange =
        createBackupWithTimestamp(
            new BackupIdentifierImpl(
                1, 1, generator.fromTimestamp(timestampInRange.toEpochMilli())),
            timestampInRange);
    final var backupBeforeRange =
        createBackupWithTimestamp(
            new BackupIdentifierImpl(
                1, 2, generator.fromTimestamp(timestampBeforeRange.toEpochMilli())),
            timestampBeforeRange);
    final var backupAfterRange =
        createBackupWithTimestamp(
            new BackupIdentifierImpl(
                1, 3, generator.fromTimestamp(timestampAfterRange.toEpochMilli())),
            timestampAfterRange);
    final var backupAtStart =
        createBackupWithTimestamp(
            new BackupIdentifierImpl(1, 4, generator.fromTimestamp(from.toEpochMilli())), from);
    final var backupAtEnd =
        createBackupWithTimestamp(
            new BackupIdentifierImpl(1, 5, generator.fromTimestamp(to.toEpochMilli())), to);

    Stream.of(backupInRange, backupBeforeRange, backupAfterRange, backupAtStart, backupAtEnd)
        .map(backup -> getStore().save(backup))
        .forEach(CompletableFuture::join);

    // when
    final var wildCard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), CheckpointPattern.ofTimeRange(from, to, generator));
    final var result = getStore().list(wildCard);

    // then - only backups strictly within the range (after from and before to)
    assertThat(result).succeedsWithin(Duration.ofSeconds(20));
    assertThat(result.join())
        .map(BackupStatus::id)
        .containsExactlyInAnyOrder(backupAtStart.id(), backupInRange.id(), backupAtEnd.id());
  }

  @Test
  default void shouldReturnEmptyListWhenNoBackupsInRange() throws IOException {
    // given
    final var from = Instant.parse("2024-01-01T00:00:00Z");
    final var to = Instant.parse("2024-01-01T12:00:00Z");
    final var generator = new CheckpointIdGenerator(0L);

    final var timestampBeforeRange = Instant.parse("2023-12-31T23:59:59Z");
    final var timestampAfterRange = Instant.parse("2024-01-01T12:00:01Z");

    final var backupBeforeRange =
        createBackupWithTimestamp(
            new BackupIdentifierImpl(
                1, 1, generator.fromTimestamp(timestampBeforeRange.toEpochMilli())),
            timestampBeforeRange);
    final var backupAfterRange =
        createBackupWithTimestamp(
            new BackupIdentifierImpl(
                1, 2, generator.fromTimestamp(timestampAfterRange.toEpochMilli())),
            timestampAfterRange);

    Stream.of(backupBeforeRange, backupAfterRange)
        .map(backup -> getStore().save(backup))
        .forEach(CompletableFuture::join);

    // when
    final var wildCard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), CheckpointPattern.ofTimeRange(from, to, generator));
    final var result = getStore().list(wildCard);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(20));
    assertThat(result.join()).isEmpty();
  }

  @Test
  default void shouldHandleMultipleBackupsInRange() throws IOException {
    // given
    final var from = Instant.parse("2024-01-01T00:00:00Z");
    final var to = Instant.parse("2024-01-01T18:00:00Z");
    final var generator = new CheckpointIdGenerator(0L);

    final var timestamp1 = Instant.parse("2024-01-01T02:00:00Z");
    final var timestamp2 = Instant.parse("2024-01-01T08:00:00Z");
    final var timestamp3 = Instant.parse("2024-01-01T14:00:00Z");

    final var backup1 =
        createBackupWithTimestamp(
            new BackupIdentifierImpl(1, 1, generator.fromTimestamp(timestamp1.toEpochMilli())),
            timestamp1);
    final var backup2 =
        createBackupWithTimestamp(
            new BackupIdentifierImpl(1, 2, generator.fromTimestamp(timestamp2.toEpochMilli())),
            timestamp2);
    final var backup3 =
        createBackupWithTimestamp(
            new BackupIdentifierImpl(1, 3, generator.fromTimestamp(timestamp3.toEpochMilli())),
            timestamp3);

    Stream.of(backup1, backup2, backup3)
        .map(backup -> getStore().save(backup))
        .forEach(CompletableFuture::join);

    // when
    final var wildCard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), CheckpointPattern.ofTimeRange(from, to, generator));
    final var result = getStore().list(wildCard);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(20));
    assertThat(result.join())
        .map(BackupStatus::id)
        .containsExactlyInAnyOrder(backup1.id(), backup2.id(), backup3.id());
  }

  default Backup createBackupWithTimestamp(final BackupIdentifierImpl id, final Instant timestamp)
      throws IOException {
    final var tempDir = Files.createTempDirectory("backup");
    Files.createDirectory(tempDir.resolve("segments/"));
    final var seg1 = Files.createFile(tempDir.resolve("segments/segment-file-1"));
    Files.write(seg1, RandomUtils.nextBytes(1));

    return new BackupImpl(
        id,
        new BackupDescriptorImpl(
            4,
            5,
            VersionUtil.getVersion(),
            timestamp.truncatedTo(ChronoUnit.MILLIS),
            CheckpointType.MANUAL_BACKUP),
        new NamedFileSetImpl(Map.of()),
        new NamedFileSetImpl(Map.of("segment-file-1", seg1)));
  }
}
