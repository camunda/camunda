/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import io.camunda.zeebe.backup.testkit.support.WildcardBackupProvider;
import io.camunda.zeebe.backup.testkit.support.WildcardBackupProvider.WildcardTestParameter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
    final var provider = new TestBackupProvider();

    final var backups =
        Stream.concat(parameter.unexpectedIds().stream(), parameter.expectedIds().stream())
            .map(
                id -> {
                  try {
                    return provider.minimalBackupWithId(id);
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
}
