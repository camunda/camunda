/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import io.camunda.auth.domain.model.SessionData;
import io.camunda.auth.domain.spi.SessionPersistencePort;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link SessionPersistencePort} using MyBatis. */
public class RdbmsSessionPersistenceAdapter implements SessionPersistencePort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsSessionPersistenceAdapter.class);

  private final WebSessionMapper mapper;

  public RdbmsSessionPersistenceAdapter(final WebSessionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public SessionData findById(final String sessionId) {
    LOG.debug("Finding web session by id={}", sessionId);
    final WebSessionEntity entity = mapper.findById(sessionId);
    return entity != null ? toDomain(entity) : null;
  }

  @Override
  public void save(final SessionData sessionData) {
    LOG.debug("Saving web session id={}", sessionData.id());
    final WebSessionEntity entity = toEntity(sessionData);
    final int updated = mapper.update(entity);
    if (updated == 0) {
      mapper.insert(entity);
    }
  }

  @Override
  public void deleteById(final String sessionId) {
    LOG.debug("Deleting web session id={}", sessionId);
    mapper.deleteById(sessionId);
  }

  @Override
  public List<SessionData> findAll() {
    LOG.debug("Finding all web sessions");
    return mapper.findAll().stream()
        .map(RdbmsSessionPersistenceAdapter::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public void deleteExpired() {
    LOG.debug("Deleting expired web sessions");
    mapper.deleteExpired(System.currentTimeMillis());
  }

  private static WebSessionEntity toEntity(final SessionData data) {
    final WebSessionEntity entity = new WebSessionEntity();
    entity.setSessionId(data.id());
    entity.setCreationTime(data.creationTime());
    entity.setLastAccessedTime(data.lastAccessedTime());
    entity.setMaxInactiveIntervalInSeconds(data.maxInactiveIntervalInSeconds());
    entity.setAttributes(data.attributes() != null ? data.attributes() : new HashMap<>());
    return entity;
  }

  private static SessionData toDomain(final WebSessionEntity entity) {
    return new SessionData(
        entity.getSessionId(),
        entity.getCreationTime(),
        entity.getLastAccessedTime(),
        entity.getMaxInactiveIntervalInSeconds(),
        entity.getAttributes() != null ? entity.getAttributes() : new HashMap<>());
  }
}
