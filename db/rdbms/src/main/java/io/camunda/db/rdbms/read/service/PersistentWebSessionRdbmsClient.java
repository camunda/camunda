/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.exception.DatabaseExceptionTranslator;
import io.camunda.db.rdbms.write.service.PersistentWebSessionWriter;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.search.query.SearchQueryResult;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * {@link PersistentWebSessionClient} backed by MyBatis. Every call is routed through {@link
 * DatabaseExceptionTranslator} so a transient database failure (e.g. a dropped or exhausted
 * connection) surfaces as a {@link io.camunda.search.exception.CamundaSearchException} with {@link
 * io.camunda.search.exception.CamundaSearchException.Reason#CONNECTION_FAILED}, the same as the
 * Elasticsearch/OpenSearch-backed client — callers such as {@code SessionStoreAdapter} rely on this
 * to retry regardless of which secondary storage is configured.
 */
public class PersistentWebSessionRdbmsClient implements PersistentWebSessionClient {

  private final PersistentWebSessionDbReader persistentWebSessionDbReader;
  private final PersistentWebSessionWriter persistentWebSessionWriter;

  public PersistentWebSessionRdbmsClient(
      final PersistentWebSessionDbReader persistentWebSessionDbReader,
      final PersistentWebSessionWriter persistentWebSessionWriter) {
    this.persistentWebSessionDbReader = persistentWebSessionDbReader;
    this.persistentWebSessionWriter = persistentWebSessionWriter;
  }

  @Override
  public @Nullable PersistentWebSessionEntity getPersistentWebSession(final String sessionId) {
    return translated(() -> persistentWebSessionDbReader.findById(sessionId));
  }

  @Override
  public void upsertPersistentWebSession(
      final PersistentWebSessionEntity persistentWebSessionEntity) {
    translated(
        () -> {
          persistentWebSessionWriter.upsert(persistentWebSessionEntity);
          return null;
        });
  }

  @Override
  public void deletePersistentWebSession(final String sessionId) {
    translated(
        () -> {
          persistentWebSessionWriter.deleteById(sessionId);
          return null;
        });
  }

  @Override
  public SearchQueryResult<PersistentWebSessionEntity> getAllPersistentWebSessions() {
    return translated(
        () -> {
          final var sessions = persistentWebSessionDbReader.findAll();
          return SearchQueryResult.of(r -> r.items(sessions));
        });
  }

  private static <T> T translated(final Supplier<T> action) {
    try {
      return action.get();
    } catch (final RuntimeException e) {
      throw DatabaseExceptionTranslator.translateIfNeeded(e);
    }
  }
}
