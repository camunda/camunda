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
import io.camunda.zeebe.backup.api.ListOptions;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import io.camunda.zeebe.backup.testkit.support.WildcardBackupProvider;
import io.camunda.zeebe.backup.testkit.support.WildcardBackupProvider.WildcardTestParameter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
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

  @Test
  default void canListManyBackups() throws IOException {
    // given
    final int backupCount = 2_000;
    final var semaphore = new Semaphore(200);
    final var ids =
        IntStream.rangeClosed(1, backupCount)
            .mapToObj(i -> new BackupIdentifierImpl(1, 1, i))
            .toList();
    final var backupTemplate = getBackup(ids.getFirst());

    CompletableFuture.allOf(
            ids.stream()
                .map(
                    id -> {
                      semaphore.acquireUninterruptibly();
                      return getStore()
                          .save(
                              new BackupImpl(
                                  id,
                                  backupTemplate.descriptor(),
                                  backupTemplate.snapshot(),
                                  backupTemplate.segments()))
                          .whenComplete((v, e) -> semaphore.release());
                    })
                .toArray(CompletableFuture[]::new))
        .join();

    // when
    final var status =
        getStore()
            .list(
                new BackupIdentifierWildcardImpl(
                    Optional.empty(), Optional.of(1), CheckpointPattern.any()));

    // then
    assertThat(status)
        // Deliberately a lower timeout than the default response timeout of 15 seconds.
        // If this is not enough, we might need to improve the implementation.
        .succeedsWithin(Duration.ofSeconds(10))
        .satisfies(statuses -> assertThat(statuses).hasSize(backupCount));

    // when
    final var firstPage =
        getStore()
            .list(
                new BackupIdentifierWildcardImpl(
                    Optional.empty(), Optional.of(1), CheckpointPattern.any()),
                ListOptions.newestFirst(OptionalLong.empty(), OptionalInt.of(100)));

    // then
    assertThat(firstPage)
        // A page must not cost a full listing: only the selected manifests may be read.
        .succeedsWithin(Duration.ofSeconds(5))
        .satisfies(
            statuses ->
                assertThat(statuses)
                    .extracting(ListingBackups::checkpointId)
                    .containsExactlyElementsOf(
                        LongStream.iterate(backupCount, i -> i - 1).limit(100).boxed().toList()));
  }

  @Test
  default void canPageNewestFirst() throws IOException {
    // given
    final var wildcard = allBackupsOfPartition(41);
    saveBackups(
        LongStream.rangeClosed(1, 7).mapToObj(id -> new BackupIdentifierImpl(1, 41, id)).toList());

    // when
    final var firstPage =
        getStore()
            .list(wildcard, ListOptions.newestFirst(OptionalLong.empty(), OptionalInt.of(3)))
            .join();
    final var secondPage =
        getStore()
            .list(wildcard, ListOptions.newestFirst(OptionalLong.of(5), OptionalInt.of(3)))
            .join();
    final var lastPage =
        getStore()
            .list(wildcard, ListOptions.newestFirst(OptionalLong.of(2), OptionalInt.of(3)))
            .join();
    final var emptyPage =
        getStore()
            .list(wildcard, ListOptions.newestFirst(OptionalLong.of(1), OptionalInt.of(3)))
            .join();

    // then
    assertThat(firstPage).extracting(ListingBackups::checkpointId).containsExactly(7L, 6L, 5L);
    assertThat(secondPage).extracting(ListingBackups::checkpointId).containsExactly(4L, 3L, 2L);
    assertThat(lastPage).extracting(ListingBackups::checkpointId).containsExactly(1L);
    assertThat(emptyPage).isEmpty();
  }

  @Test
  default void canPageOldestFirst() throws IOException {
    // given
    final var wildcard = allBackupsOfPartition(42);
    saveBackups(
        LongStream.rangeClosed(1, 7).mapToObj(id -> new BackupIdentifierImpl(1, 42, id)).toList());

    // when
    final var firstPage =
        getStore()
            .list(wildcard, ListOptions.oldestFirst(OptionalLong.empty(), OptionalInt.of(3)))
            .join();
    final var secondPage =
        getStore()
            .list(wildcard, ListOptions.oldestFirst(OptionalLong.of(3), OptionalInt.of(3)))
            .join();
    final var lastPage =
        getStore()
            .list(wildcard, ListOptions.oldestFirst(OptionalLong.of(6), OptionalInt.of(3)))
            .join();

    // then
    assertThat(firstPage).extracting(ListingBackups::checkpointId).containsExactly(1L, 2L, 3L);
    assertThat(secondPage).extracting(ListingBackups::checkpointId).containsExactly(4L, 5L, 6L);
    assertThat(lastPage).extracting(ListingBackups::checkpointId).containsExactly(7L);
  }

  @Test
  default void shouldOrderPagesByNumericCheckpointId() throws IOException {
    // given: ids of different lengths sort differently as strings than as numbers
    final var wildcard = allBackupsOfPartition(43);
    saveBackups(
        LongStream.of(1, 2, 10, 100, 1_700_000_000_000L)
            .mapToObj(id -> new BackupIdentifierImpl(1, 43, id))
            .toList());

    // when
    final var newestFirst = getStore().list(wildcard, ListOptions.all()).join();
    final var oldestFirst =
        getStore()
            .list(wildcard, ListOptions.oldestFirst(OptionalLong.empty(), OptionalInt.empty()))
            .join();

    // then
    assertThat(newestFirst)
        .extracting(ListingBackups::checkpointId)
        .containsExactly(1_700_000_000_000L, 100L, 10L, 2L, 1L);
    assertThat(oldestFirst)
        .extracting(ListingBackups::checkpointId)
        .containsExactly(1L, 2L, 10L, 100L, 1_700_000_000_000L);
  }

  @Test
  default void shouldCountPageLimitByCheckpointId() throws IOException {
    // given: two brokers stored checkpoints 1 and 2, one broker stored checkpoint 3
    final var wildcard = allBackupsOfPartition(44);
    saveBackups(
        List.of(
            new BackupIdentifierImpl(1, 44, 1),
            new BackupIdentifierImpl(2, 44, 1),
            new BackupIdentifierImpl(1, 44, 2),
            new BackupIdentifierImpl(2, 44, 2),
            new BackupIdentifierImpl(1, 44, 3)));

    // when
    final var page =
        getStore()
            .list(wildcard, ListOptions.newestFirst(OptionalLong.empty(), OptionalInt.of(2)))
            .join();

    // then
    assertThat(page).extracting(ListingBackups::checkpointId).containsExactly(3L, 2L, 2L);
    assertThat(page)
        .extracting(BackupStatus::id)
        .containsExactlyInAnyOrder(
            new BackupIdentifierImpl(1, 44, 3),
            new BackupIdentifierImpl(1, 44, 2),
            new BackupIdentifierImpl(2, 44, 2));
  }

  @Test
  default void shouldCombineCheckpointPrefixAndCursor() throws IOException {
    // given
    saveBackups(
        LongStream.of(1, 10, 20, 100, 101)
            .mapToObj(id -> new BackupIdentifierImpl(1, 45, id))
            .toList());
    final var wildcard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.of(45), CheckpointPattern.of("10*"));

    // when
    final var page =
        getStore()
            .list(wildcard, ListOptions.newestFirst(OptionalLong.of(101), OptionalInt.of(5)))
            .join();

    // then
    assertThat(page).extracting(ListingBackups::checkpointId).containsExactly(100L, 10L);
  }

  default Backup getBackup(final BackupIdentifierImpl id) throws IOException {
    return TestBackupProvider.minimalBackupWithId(id);
  }

  default void saveBackups(final List<BackupIdentifierImpl> ids) throws IOException {
    for (final var id : ids) {
      getStore().save(getBackup(id)).join();
    }
  }

  private static BackupIdentifierWildcardImpl allBackupsOfPartition(final int partitionId) {
    return new BackupIdentifierWildcardImpl(
        Optional.empty(), Optional.of(partitionId), CheckpointPattern.any());
  }

  private static long checkpointId(final BackupStatus status) {
    return status.id().checkpointId();
  }
}
