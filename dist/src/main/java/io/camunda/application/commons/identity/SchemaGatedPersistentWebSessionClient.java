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
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skips {@link #upsertPersistentWebSession} while the physical tenant's search schema is not yet
 * initialized, instead of delegating straight through to Elasticsearch/OpenSearch (issue #58509).
 *
 * <p>Before the schema manager has created the {@code web-session} index, a write reaches ES/OS's
 * {@code auto_create_index} and fabricates a poison index with no alias and the wrong dynamic
 * mappings; {@code IndexSchemaValidator} then hard-fails on every subsequent startup, crash-looping
 * the gateway. Callers already tolerate a lost session write the same way they tolerate one lost to
 * a transient ES/OS outage (retry-then-swallow-with-WARN in {@code SessionStoreAdapter}), so
 * skipping here is a safe degrade, not a new failure mode.
 *
 * <p>Reads and deletes are never gated: {@code get}/{@code getAll} don't write, and ES/OS delete
 * uses internal versioning that does not trigger {@code auto_create_index}.
 */
final class SchemaGatedPersistentWebSessionClient implements PersistentWebSessionClient {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SchemaGatedPersistentWebSessionClient.class);

  private final PersistentWebSessionClient delegate;
  private final String physicalTenantId;
  private final BooleanSupplier isSchemaInitialized;

  SchemaGatedPersistentWebSessionClient(
      final PersistentWebSessionClient delegate,
      final String physicalTenantId,
      final BooleanSupplier isSchemaInitialized) {
    this.delegate = delegate;
    this.physicalTenantId = physicalTenantId;
    this.isSchemaInitialized = isSchemaInitialized;
  }

  @Override
  public PersistentWebSessionEntity getPersistentWebSession(final String sessionId) {
    return delegate.getPersistentWebSession(sessionId);
  }

  @Override
  public void upsertPersistentWebSession(
      final PersistentWebSessionEntity persistentWebSessionEntity) {
    if (!isSchemaInitialized.getAsBoolean()) {
      LOGGER.warn(
          "Skipping a persistent web session upsert on physical tenant '{}': "
              + "search schema is not initialized yet",
          physicalTenantId);
      return;
    }
    delegate.upsertPersistentWebSession(persistentWebSessionEntity);
  }

  @Override
  public void deletePersistentWebSession(final String sessionId) {
    delegate.deletePersistentWebSession(sessionId);
  }

  @Override
  public SearchQueryResult<PersistentWebSessionEntity> getAllPersistentWebSessions() {
    return delegate.getAllPersistentWebSessions();
  }
}
