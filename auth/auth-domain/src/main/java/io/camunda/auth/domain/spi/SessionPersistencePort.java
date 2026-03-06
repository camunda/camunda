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

  List<SessionData> findAll();
}
