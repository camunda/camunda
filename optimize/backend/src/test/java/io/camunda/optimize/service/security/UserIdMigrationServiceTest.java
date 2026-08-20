/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.db.repository.UserIdMigrationRepository;
import org.junit.jupiter.api.Test;

class UserIdMigrationServiceTest {

  private static final String OLD_USER_ID = "old-user-id";
  private static final String NEW_USER_ID = "new-user-id";

  @Test
  void successfulMigrationIsNeverRepeatedOnSubsequentCalls() {
    final UserIdMigrationRepository repository = mock(UserIdMigrationRepository.class);
    when(repository.hasDocumentsWithUserId(OLD_USER_ID)).thenReturn(true, false);
    final UserIdMigrationService service = new UserIdMigrationService(repository);

    service.migrateUserIdIfNeeded(NEW_USER_ID, OLD_USER_ID);

    // wait for the full cycle to finish: the loop's second hasDocumentsWithUserId check (which
    // returns false and ends the migration) only happens after the backoff sleep following the
    // first migrateUserId call
    given()
        .timeout(5, SECONDS)
        .untilAsserted(
            () -> {
              verify(repository, times(1)).migrateUserId(OLD_USER_ID, NEW_USER_ID);
              verify(repository, times(2)).hasDocumentsWithUserId(OLD_USER_ID);
            });

    // old ID already migrated: further calls must not re-check or re-migrate
    service.migrateUserIdIfNeeded(NEW_USER_ID, OLD_USER_ID);
    service.migrateUserIdIfNeeded(NEW_USER_ID, OLD_USER_ID);

    verify(repository, times(2)).hasDocumentsWithUserId(OLD_USER_ID);
    verify(repository, times(1)).migrateUserId(OLD_USER_ID, NEW_USER_ID);
  }

  @Test
  void failedMigrationIsRetriedOnNextCall() {
    final UserIdMigrationRepository repository = mock(UserIdMigrationRepository.class);
    // old ID never disappears: every attempt exhausts and the migration is considered failed
    when(repository.hasDocumentsWithUserId(OLD_USER_ID)).thenReturn(true);
    final UserIdMigrationService service = new UserIdMigrationService(repository);

    service.migrateUserIdIfNeeded(NEW_USER_ID, OLD_USER_ID);

    given()
        .timeout(10, SECONDS)
        .untilAsserted(() -> verify(repository, times(3)).migrateUserId(OLD_USER_ID, NEW_USER_ID));

    // the failed migration is only removed from the handled set once whenComplete runs, which
    // races with this thread; retry the trigger call on every poll until it takes effect
    given()
        .timeout(10, SECONDS)
        .untilAsserted(
            () -> {
              service.migrateUserIdIfNeeded(NEW_USER_ID, OLD_USER_ID);
              verify(repository, times(6)).migrateUserId(OLD_USER_ID, NEW_USER_ID);
            });
  }

  @Test
  void concurrentCallsForSameOldUserIdMigrateOnlyOnce() {
    final UserIdMigrationRepository repository = mock(UserIdMigrationRepository.class);
    when(repository.hasDocumentsWithUserId(OLD_USER_ID)).thenReturn(true, false);
    final UserIdMigrationService service = new UserIdMigrationService(repository);

    service.migrateUserIdIfNeeded(NEW_USER_ID, OLD_USER_ID);
    service.migrateUserIdIfNeeded(NEW_USER_ID, OLD_USER_ID);

    given()
        .timeout(5, SECONDS)
        .untilAsserted(() -> verify(repository, times(1)).migrateUserId(OLD_USER_ID, NEW_USER_ID));
  }

  @Test
  void migrationForDifferentOldUserIdsRunsIndependently() {
    final UserIdMigrationRepository repository = mock(UserIdMigrationRepository.class);
    final String otherOldUserId = "other-old-user-id";
    when(repository.hasDocumentsWithUserId(OLD_USER_ID)).thenReturn(true, false);
    when(repository.hasDocumentsWithUserId(otherOldUserId)).thenReturn(true, false);
    final UserIdMigrationService service = new UserIdMigrationService(repository);

    service.migrateUserIdIfNeeded(NEW_USER_ID, OLD_USER_ID);
    service.migrateUserIdIfNeeded(NEW_USER_ID, otherOldUserId);

    given()
        .timeout(5, SECONDS)
        .untilAsserted(
            () -> {
              verify(repository, times(1)).migrateUserId(OLD_USER_ID, NEW_USER_ID);
              verify(repository, times(1)).migrateUserId(otherOldUserId, NEW_USER_ID);
            });
  }
}
