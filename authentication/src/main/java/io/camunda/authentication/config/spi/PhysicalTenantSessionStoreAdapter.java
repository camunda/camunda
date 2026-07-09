/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.security.api.model.session.PersistentSession;
import io.camunda.security.core.port.out.SessionStorePort;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link SessionStorePort} bound to a <b>single</b> physical tenant's {@link
 * PersistentWebSessionClient}. Every operation reads and writes exactly that tenant's secondary
 * storage — there is no request-context routing and no cross-tenant fan-out.
 *
 * <p>Scoped security chains use one instance per scope (via {@code ScopedSessionStorePortProvider},
 * ADR-0029), so a persistent session read/write routes to the correct store <em>structurally</em> —
 * decided by which scoped {@code SessionRepositoryFilter} handles the request — even during Spring
 * Session's commit phase, which runs after the request scope is torn down.
 *
 * <p>Upserts are retried on transient storage failures with exponential backoff; once retries are
 * exhausted the failure is logged and swallowed so a storage blip never propagates into the session
 * save path. This resilience policy lives here (rather than in the library) because it inspects the
 * search-specific {@link CamundaSearchException} reasons to decide what is transient.
 */
public final class PhysicalTenantSessionStoreAdapter implements SessionStorePort {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PhysicalTenantSessionStoreAdapter.class);
  private static final int MAX_RETRY_ATTEMPTS = 3;
  private static final long INITIAL_RETRY_DELAY_MS = 100;

  private static final Retry UPSERT_RETRY =
      Retry.of(
          "web-session-upsert",
          RetryConfig.custom()
              .maxAttempts(MAX_RETRY_ATTEMPTS)
              .intervalFunction(IntervalFunction.ofExponentialBackoff(INITIAL_RETRY_DELAY_MS, 2))
              .retryOnException(PhysicalTenantSessionStoreAdapter::isTransientFailure)
              .build());

  private final PersistentWebSessionClient client;

  public PhysicalTenantSessionStoreAdapter(final PersistentWebSessionClient client) {
    this.client = client;
  }

  @Override
  public PersistentSession get(final String sessionId) {
    return toPersistentSession(client.getPersistentWebSession(sessionId));
  }

  @Override
  public void upsert(final PersistentSession session) {
    final var entity = toEntity(session);
    try {
      Retry.decorateRunnable(UPSERT_RETRY, () -> client.upsertPersistentWebSession(entity)).run();
    } catch (final CamundaSearchException e) {
      LOGGER.warn(
          "Failed to save web session to persistent storage after {} attempts: {} (reason: {})",
          MAX_RETRY_ATTEMPTS,
          e.getMessage(),
          e.getReason(),
          e);
    } catch (final RuntimeException e) {
      LOGGER.warn(
          "Failed to save web session to persistent storage after {} attempts: {}",
          MAX_RETRY_ATTEMPTS,
          e.getMessage(),
          e);
    }
  }

  @Override
  public void delete(final String sessionId) {
    client.deletePersistentWebSession(sessionId);
  }

  @Override
  public List<PersistentSession> getAll() {
    final var result = new ArrayList<PersistentSession>();
    final var items = client.getAllPersistentWebSessions().items();
    if (items != null) {
      items.stream()
          .map(PhysicalTenantSessionStoreAdapter::toPersistentSession)
          .forEach(result::add);
    }
    return result;
  }

  static boolean isTransientFailure(final Throwable throwable) {
    if (throwable instanceof final CamundaSearchException cse) {
      return switch (cse.getReason()) {
        case CONNECTION_FAILED, SEARCH_CLIENT_FAILED, SEARCH_SERVER_FAILED, UNKNOWN -> true;
        default -> false;
      };
    }
    return throwable instanceof RuntimeException;
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
