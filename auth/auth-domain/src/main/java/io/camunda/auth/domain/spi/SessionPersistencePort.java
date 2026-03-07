/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.spi;

import io.camunda.auth.domain.model.SessionData;
import java.util.List;

/** SPI for persisting web sessions. Implementations provide the storage backend. */
public interface SessionPersistencePort {
  SessionData findById(String sessionId);

  void save(SessionData sessionData);

  void deleteById(String sessionId);

  /**
   * Returns all persisted sessions.
   *
   * @deprecated Use {@link #deleteExpired()} instead, which allows backends to handle expiry
   *     natively without loading all sessions into memory.
   */
  @Deprecated
  List<SessionData> findAll();

  /**
   * Deletes all expired sessions from the underlying store. Implementations should handle expiry
   * natively (e.g. via a database query) rather than loading all sessions into memory.
   *
   * <p>The default implementation falls back to {@link #findAll()} filtering, which is kept only
   * for backward compatibility with existing implementations.
   */
  default void deleteExpired() {
    final long now = System.currentTimeMillis();
    findAll().stream()
        .filter(
            session ->
                session.lastAccessedTime() + (session.maxInactiveIntervalInSeconds() * 1000) < now)
        .forEach(session -> deleteById(session.id()));
  }
}
