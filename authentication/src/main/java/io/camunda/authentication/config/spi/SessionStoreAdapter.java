/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import io.camunda.authentication.exception.CamundaAuthenticationException;
import io.camunda.authentication.utils.OutageLog;
import io.camunda.authentication.utils.TransientRetry;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.security.api.model.session.PersistentSession;
import io.camunda.security.core.port.out.SessionStorePort;
import io.github.resilience4j.retry.Retry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link SessionStorePort} bound to a <b>single</b> physical tenant's {@link
 * PersistentWebSessionClient}. Every operation reads and writes exactly that tenant's secondary
 * storage — there is no request-context routing and no cross-tenant fan-out.
 *
 * <p>Scoped security chains use one instance per scope (via {@code ScopedSessionStorePortProvider},
 * CSL ADR-0029), so a persistent session read/write routes to the correct store
 * <em>structurally</em> — decided by which scoped {@code SessionRepositoryFilter} handles the
 * request — even during Spring Session's commit phase, which runs after the request scope is torn
 * down.
 *
 * <p>Every operation retries transient storage failures with exponential backoff. All but {@link
 * #delete} then log and swallow, so a storage blip never fails the request: reads degrade to
 * "nothing found" and an unsaved session is simply lost, both of which grant less access rather
 * than more. Deletion is the one operation whose degraded outcome grants <em>more</em>, so it
 * propagates instead, as a {@link CamundaAuthenticationException} — no backend's exception type
 * crosses this boundary. This policy lives here rather than in the library because it inspects the
 * search-specific {@link CamundaSearchException} reasons to decide what is transient.
 */
public final class SessionStoreAdapter implements SessionStorePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(SessionStoreAdapter.class);

  private static final Retry GET_RETRY = TransientRetry.of("web-session-get");
  private static final Retry UPSERT_RETRY = TransientRetry.of("web-session-upsert");
  private static final Retry DELETE_RETRY = TransientRetry.of("web-session-delete");
  private static final Retry GET_ALL_RETRY = TransientRetry.of("web-session-get-all");

  private final PersistentWebSessionClient client;

  // Per adapter, so an outage of one physical tenant's store never suppresses the report for
  // another; keyed by operation, so a failing read does not suppress the report of a failing write.
  private final Map<String, OutageLog> outageLogs = new ConcurrentHashMap<>();

  public SessionStoreAdapter(final PersistentWebSessionClient client) {
    this.client = client;
  }

  @Override
  public PersistentSession get(final String sessionId) {
    return toPersistentSession(
        runWithRetry(GET_RETRY, "read", () -> client.getPersistentWebSession(sessionId), null));
  }

  @Override
  public void upsert(final PersistentSession session) {
    final var entity = toEntity(session);
    runWithRetry(UPSERT_RETRY, "save", () -> client.upsertPersistentWebSession(entity));
  }

  @Override
  public void delete(final String sessionId) {
    // Retried but never swallowed: a failed delete leaves the record readable with its original
    // authenticated context, so reporting the invalidation as done would let a copied cookie be
    // replayed until the session expires on its own.
    try {
      Retry.decorateRunnable(DELETE_RETRY, () -> client.deletePersistentWebSession(sessionId))
          .run();
    } catch (final RuntimeException e) {
      // Every RuntimeException, not just CamundaSearchException: the RDBMS-backed client calls
      // MyBatis with no exception translation, so catching the search type alone would leak the
      // persistence layer's exceptions through the port on that backend.
      //
      // The session id is the cookie value, so it stays out of the message — an exception message
      // ends up in a log, and a logged session id is a replayable one.
      throw new CamundaAuthenticationException("Failed to delete the persistent web session", e);
    }
  }

  @Override
  public List<PersistentSession> getAll() {
    final var items =
        runWithRetry(
            GET_ALL_RETRY, "read all", () -> client.getAllPersistentWebSessions().items(), null);
    final var result = new ArrayList<PersistentSession>();
    if (items != null) {
      items.stream().map(SessionStoreAdapter::toPersistentSession).forEach(result::add);
    }
    return result;
  }

  private <T> T runWithRetry(
      final Retry retry, final String operation, final Supplier<T> action, final T fallback) {
    final var attempts = new AtomicInteger();
    final Supplier<T> countedAction =
        () -> {
          attempts.incrementAndGet();
          return action.get();
        };
    final var outage = outageLogs.computeIfAbsent(operation, op -> new OutageLog(LOGGER));
    try {
      final var result = Retry.decorateSupplier(retry, countedAction).get();
      outage.recovery("Persistent web session {} works again", operation);
      return result;
    } catch (final CamundaSearchException e) {
      outage.failure(
          "Failed to {} persistent web session after {} attempt(s): {} (reason: {})",
          operation,
          attempts.get(),
          e.getMessage(),
          e.getReason(),
          e);
      return fallback;
    } catch (final RuntimeException e) {
      outage.failure(
          "Failed to {} persistent web session after {} attempt(s): {}",
          operation,
          attempts.get(),
          e.getMessage(),
          e);
      return fallback;
    }
  }

  private void runWithRetry(final Retry retry, final String operation, final Runnable action) {
    runWithRetry(
        retry,
        operation,
        () -> {
          action.run();
          return null;
        },
        null);
  }

  static PersistentSession toPersistentSession(final PersistentWebSessionEntity entity) {
    if (entity == null) {
      return null;
    }
    return new PersistentSession(
        entity.id(),
        entity.creationTime(),
        entity.lastAccessedTime(),
        entity.maxInactiveIntervalInSeconds(),
        entity.attributes());
  }

  static PersistentWebSessionEntity toEntity(final PersistentSession session) {
    return new PersistentWebSessionEntity(
        session.id(),
        session.creationTime(),
        session.lastAccessedTime(),
        session.maxInactiveIntervalInSeconds(),
        session.attributes());
  }
}
