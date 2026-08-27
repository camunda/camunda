/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import io.camunda.authentication.utils.TransientRetry;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.security.api.model.session.PersistentSession;
import io.camunda.security.core.port.out.SessionStorePort;
import io.github.resilience4j.retry.Retry;
import java.util.ArrayList;
import java.util.List;
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
 * <p>Every operation is retried on transient storage failures with exponential backoff; once
 * retries are exhausted (or the failure is not transient), the failure is logged and swallowed so a
 * storage blip never fails the request. Reads (get, getAll) degrade to "nothing found"; writes
 * (upsert, delete) are simply dropped. This resilience policy lives here (rather than in the
 * library) because it inspects the search-specific {@link CamundaSearchException} reasons to decide
 * what is transient.
 */
public final class SessionStoreAdapter implements SessionStorePort {

  private static final Logger LOGGER = LoggerFactory.getLogger(SessionStoreAdapter.class);

  private static final Retry GET_RETRY = TransientRetry.of("web-session-get");
  private static final Retry UPSERT_RETRY = TransientRetry.of("web-session-upsert");
  private static final Retry DELETE_RETRY = TransientRetry.of("web-session-delete");
  private static final Retry GET_ALL_RETRY = TransientRetry.of("web-session-get-all");

  private final PersistentWebSessionClient client;

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
    runWithRetry(DELETE_RETRY, "delete", () -> client.deletePersistentWebSession(sessionId));
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

  private static <T> T runWithRetry(
      final Retry retry, final String operation, final Supplier<T> action, final T fallback) {
    try {
      return Retry.decorateSupplier(retry, action).get();
    } catch (final CamundaSearchException e) {
      LOGGER.warn(
          "Failed to {} persistent web session after {} attempts: {} (reason: {})",
          operation,
          TransientRetry.MAX_ATTEMPTS,
          e.getMessage(),
          e.getReason(),
          e);
      return fallback;
    } catch (final RuntimeException e) {
      LOGGER.warn(
          "Failed to {} persistent web session after {} attempts: {}",
          operation,
          TransientRetry.MAX_ATTEMPTS,
          e.getMessage(),
          e);
      return fallback;
    }
  }

  private static void runWithRetry(
      final Retry retry, final String operation, final Runnable action) {
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
