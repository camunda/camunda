/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.write.service.PersistentWebSessionWriter;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.search.query.SearchQueryResult;

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
  public PersistentWebSessionEntity getPersistentWebSession(final String sessionId) {
    return persistentWebSessionDbReader.findById(sessionId);
  }

  @Override
  public void upsertPersistentWebSession(
      final PersistentWebSessionEntity persistentWebSessionEntity) {
    persistentWebSessionWriter.upsert(persistentWebSessionEntity);
  }

  @Override
  public void deletePersistentWebSession(final String sessionId) {
    persistentWebSessionWriter.deleteById(sessionId);
  }

  @Override
  public SearchQueryResult<PersistentWebSessionEntity> getAllPersistentWebSessions() {
    final var sessions = persistentWebSessionDbReader.findAll();
    return SearchQueryResult.of(r -> r.items(sessions));
  }
}
