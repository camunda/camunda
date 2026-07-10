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
import io.camunda.search.query.SearchQueryResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class PersistentWebSessionClientStub implements PersistentWebSessionClient {

  private final Map<String, PersistentWebSessionEntity> sessions = new HashMap<>();

  @Override
  public PersistentWebSessionEntity getPersistentWebSession(final String sessionId) {
    return sessions.get(sessionId);
  }

  @Override
  public void upsertPersistentWebSession(final PersistentWebSessionEntity entity) {
    sessions.put(entity.id(), entity);
  }

  @Override
  public void deletePersistentWebSession(final String sessionId) {
    sessions.remove(sessionId);
  }

  @Override
  public SearchQueryResult<PersistentWebSessionEntity> getAllPersistentWebSessions() {
    return SearchQueryResult.of(b -> b.items(new ArrayList<>(sessions.values())));
  }
}
