/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import io.camunda.optimize.service.db.repository.UserIdMigrationRepository;
import io.camunda.optimize.service.exceptions.UserIdMigrationException;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * Triggered at login when a user's SaaS identity has changed (i.e. they added a new SSO login
 * method). Checks whether any Optimize documents still reference the old user ID, and if so,
 * rewrites them asynchronously so the user can access their entities without waiting.
 *
 * <p>Once a login method change has happened, no new document will ever be written under the old
 * user ID again, so a successful migration never needs to be repeated: {@link #handledOldUserIds}
 * marks the old ID as done permanently, and subsequent logins skip the work (and the {@code _count}
 * check) entirely. A failed migration is removed from that set so the next login retries from
 * scratch. The same set also dedupes concurrent logins (e.g. logout/login while a migration is
 * still running) so they don't fire duplicate migrations for the same old ID.
 *
 * <p>{@link #handledOldUserIds} is in-memory only and is lost on restart. This is harmless: for an
 * old ID that was already migrated before the restart, the only consequence is one extra (cheap)
 * {@code _count} query on that user's next login, which correctly finds no matching documents and
 * does no further work.
 *
 * <p>All blocking DB work runs on a dedicated virtual-thread-per-task {@link #executor} rather than
 * the default {@code ForkJoinPool.commonPool()}: {@code _update_by_query} completion is awaited by
 * polling in a loop with no overall deadline, so on a low-core pod (where the shared common pool
 * may have as few as one worker thread) a single stuck task could otherwise starve every other
 * feature in the JVM relying on the default pool. Virtual threads (as already used for similarly
 * blocking, I/O-bound work in {@code SchemaManager} and {@code RestoreManager}) don't pin a scarce
 * platform thread while parked in that poll loop, so there's no fixed budget to exhaust even under
 * several simultaneous stuck migrations. {@link #ATTEMPT_TIMEOUT} additionally bounds each attempt
 * so a stuck task is treated as a failure and retried on the next login instead of leaving the old
 * ID permanently marked as in-progress; note this only frees the {@link CompletableFuture} chain,
 * the underlying poll loop keeps its virtual thread parked until the stuck task actually resolves.
 */
@Service
@Conditional(CCSaaSCondition.class)
public class UserIdMigrationService {

  /**
   * Caps the self-healing retry loop below. Bounds worst-case work if updates keep getting skipped
   * (e.g. persistent version conflicts) instead of retrying forever; if the old ID is still
   * referenced after this many attempts, the migration is considered failed and retried on the next
   * login.
   */
  private static final int MAX_ATTEMPTS = 3;

  private static final Duration INITIAL_RETRY_BACKOFF = Duration.ofMillis(1000);

  /**
   * Bounds how long a single attempt (the _count check plus, if needed, the _update_by_query wait)
   * may take before it's treated as failed. Generous on purpose: legitimate migrations across large
   * indices can take a while, and the cost of a false-positive timeout is just an extra retry on
   * the next login.
   */
  private static final Duration ATTEMPT_TIMEOUT = Duration.ofMinutes(10);

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(UserIdMigrationService.class);

  private final UserIdMigrationRepository repository;
  private final Set<String> handledOldUserIds = ConcurrentHashMap.newKeySet();
  private final ExecutorService executor =
      Executors.newThreadPerTaskExecutor(
          Thread.ofVirtual().name("user-id-migration-", 0).factory());

  public UserIdMigrationService(final UserIdMigrationRepository repository) {
    this.repository = repository;
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdownNow();
  }

  /**
   * Fires an async migration if any entity still references oldUserId. Safe to call on every login:
   * a no-op once the old ID has been migrated successfully, or while a migration for it is already
   * running.
   */
  public void migrateUserIdIfNeeded(final String newUserId, final String oldUserId) {
    if (!handledOldUserIds.add(oldUserId)) {
      LOG.debug("Old user ID [{}] already migrated or migration in progress, skipping", oldUserId);
      return;
    }
    CompletableFuture.supplyAsync(() -> repository.hasDocumentsWithUserId(oldUserId), executor)
        .orTimeout(ATTEMPT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
        .thenCompose(
            hasDocuments -> {
              if (!hasDocuments) {
                LOG.debug("No documents found for old user ID [{}], skipping migration", oldUserId);
                return CompletableFuture.<Void>completedFuture(null);
              }
              return runMigrationAttempt(newUserId, oldUserId, 1);
            })
        .whenComplete(
            (result, error) -> {
              if (error != null) {
                final Throwable cause = error.getCause() != null ? error.getCause() : error;
                LOG.error(
                    "Failed to migrate user ID [{}] to [{}]: {}",
                    oldUserId,
                    newUserId,
                    cause.getMessage(),
                    cause);
                handledOldUserIds.remove(oldUserId);
              }
            });
  }

  /**
   * Runs one migration attempt, then checks whether the old ID is still referenced. If it isn't,
   * the migration is done. Otherwise, if attempts remain, schedules the next one via {@link
   * CompletableFuture#delayedExecutor} (so retries never block a thread sleeping) with a backoff
   * delay between attempts 1→2 and 2→3; on the {@code MAX_ATTEMPTS}'th attempt this throws instead
   * of scheduling a retry.
   */
  private CompletableFuture<Void> runMigrationAttempt(
      final String newUserId, final String oldUserId, final int attempt) {
    LOG.info(
        "Old user ID [{}] still referenced in documents, migrating to [{}] (attempt {}/{})",
        oldUserId,
        newUserId,
        attempt,
        MAX_ATTEMPTS);
    return CompletableFuture.supplyAsync(
            () -> {
              repository.migrateUserId(oldUserId, newUserId);
              return repository.hasDocumentsWithUserId(oldUserId);
            },
            executor)
        .orTimeout(ATTEMPT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
        .thenCompose(
            stillHasDocuments -> {
              if (!stillHasDocuments) {
                LOG.info(
                    "Migration of user ID [{}] to [{}] complete after {} attempt(s)",
                    oldUserId,
                    newUserId,
                    attempt);
                return CompletableFuture.<Void>completedFuture(null);
              }
              if (attempt >= MAX_ATTEMPTS) {
                throw new UserIdMigrationException(
                    "Old user ID ["
                        + oldUserId
                        + "] still referenced in documents after "
                        + MAX_ATTEMPTS
                        + " migration attempts to ["
                        + newUserId
                        + "]");
              }
              return afterBackoff(attempt)
                  .thenCompose(ignored -> runMigrationAttempt(newUserId, oldUserId, attempt + 1));
            });
  }

  private CompletableFuture<Void> afterBackoff(final int attempt) {
    final long backoffMillis = INITIAL_RETRY_BACKOFF.toMillis() * (1L << (attempt - 1));
    return CompletableFuture.runAsync(
        () -> {},
        CompletableFuture.delayedExecutor(backoffMillis, TimeUnit.MILLISECONDS, executor));
  }
}
