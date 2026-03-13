/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spi;

import io.camunda.gatekeeper.model.session.SessionData;
import java.util.Optional;

/** SPI for persisting web sessions. Implementations provide the storage backend. */
public interface SessionPersistencePort {

  Optional<SessionData> findById(String id);

  void save(SessionData sessionData);

  void deleteById(String id);

  void deleteExpired();
}
