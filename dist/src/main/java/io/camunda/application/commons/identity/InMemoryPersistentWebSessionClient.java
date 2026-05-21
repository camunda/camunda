/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.search.query.SearchQueryResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Per-tenant in-memory {@link PersistentWebSessionClient} used by the Physical Tenant PoC.
 *
 * <p>Each tenant chain owns its own instance; the backing map is private to the instance, so
 * sessions stored under tenant A are structurally unreachable from tenant B — there is no shared
 * backend, no key-prefixing decorator, and no cross-tenant lookup path. Storage isolation is proven
 * at the backend layer.
 *
 * <p><b>PoC limitation:</b> durability is sacrificed. A production wiring would substitute a
 * per-tenant {@code PersistentWebSessionRdbmsClient} (one {@code SqlSessionFactory} per tenant) or
 * {@code PersistentWebSessionSearchImpl} (one {@code SearchClients} per tenant). Those per-tenant
 * primitives exist for data-sources / search-clients but the matching per-tenant MyBatis
 * infrastructure (per-tenant {@code SqlSessionFactory} + per-tenant {@code
 * PersistentWebSessionMapper}) is not yet exposed in this branch — building it is a separate
 * concern from the security wiring this PoC validates.
 */
@NullMarked
public final class InMemoryPersistentWebSessionClient implements PersistentWebSessionClient {

  private final Map<String, PersistentWebSessionEntity> store = new ConcurrentHashMap<>();

  @Override
  public @Nullable PersistentWebSessionEntity getPersistentWebSession(final String sessionId) {
    return store.get(sessionId);
  }

  @Override
  public void upsertPersistentWebSession(final PersistentWebSessionEntity entity) {
    store.put(entity.id(), entity);
  }

  @Override
  public void deletePersistentWebSession(final String sessionId) {
    store.remove(sessionId);
  }

  @Override
  public SearchQueryResult<PersistentWebSessionEntity> getAllPersistentWebSessions() {
    return SearchQueryResult.of(b -> b.items(List.copyOf(store.values())));
  }
}
