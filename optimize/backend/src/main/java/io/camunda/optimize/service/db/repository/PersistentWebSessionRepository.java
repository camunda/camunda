/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.dto.optimize.query.WebSessionDto;
import java.util.List;
import java.util.Optional;

/**
 * Storage of the server-side web sessions held in the {@code web-session} index. Backs {@code
 * OptimizeSessionStoreAdapter}, which is what the Camunda Security Library persists sessions
 * through.
 */
public interface PersistentWebSessionRepository {

  /** Returns the stored session, or empty when no session with that id exists. */
  Optional<WebSessionDto> get(String sessionId);

  /** Inserts the session, or replaces it when one with the same id already exists. */
  void upsert(WebSessionDto session);

  /** Deletes the session; a no-op when no session with that id exists. */
  void delete(String sessionId);

  /** Returns every stored session, so expired ones can be swept. */
  List<WebSessionDto> getAll();
}
