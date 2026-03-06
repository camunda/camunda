/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.session;

import io.camunda.auth.domain.model.SessionData;
import io.camunda.auth.domain.spi.SessionPersistencePort;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import java.util.List;
import java.util.Optional;

/**
 * Adapter that bridges the auth library's SessionPersistencePort SPI to the existing
 * PersistentWebSessionClient from the search module.
 */
public class SessionPersistenceAdapter implements SessionPersistencePort {

  private final PersistentWebSessionClient client;

  public SessionPersistenceAdapter(final PersistentWebSessionClient client) {
    this.client = client;
  }

  @Override
  public SessionData findById(final String sessionId) {
    return Optional.ofNullable(client.getPersistentWebSession(sessionId))
        .map(SessionPersistenceAdapter::toSessionData)
        .orElse(null);
  }

  @Override
  public void save(final SessionData sessionData) {
    client.upsertPersistentWebSession(toEntity(sessionData));
  }

  @Override
  public void deleteById(final String sessionId) {
    client.deletePersistentWebSession(sessionId);
  }

  @Override
  public List<SessionData> findAll() {
    return Optional.ofNullable(client.getAllPersistentWebSessions().items())
        .map(entities -> entities.stream().map(SessionPersistenceAdapter::toSessionData).toList())
        .orElse(List.of());
  }

  private static SessionData toSessionData(final PersistentWebSessionEntity entity) {
    return new SessionData(
        entity.id(),
        entity.creationTime(),
        entity.lastAccessedTime(),
        entity.maxInactiveIntervalInSeconds(),
        entity.attributes());
  }

  private static PersistentWebSessionEntity toEntity(final SessionData data) {
    return new PersistentWebSessionEntity(
        data.id(),
        data.creationTime(),
        data.lastAccessedTime(),
        data.maxInactiveIntervalInSeconds(),
        data.attributes());
  }
}
