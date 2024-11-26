/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.entities.PersistentWebSessionEntity;
import java.util.List;

public interface PersistentSessionSearchClient {

  PersistentWebSessionEntity getPersistentWebSession(final String sessionId);

  void upsertPersistentWebSession(final PersistentWebSessionEntity webSession);

  void deletePersistentWebSession(final String sessionId);

  List<PersistentWebSessionEntity> getAllPersistentWebSessions();
}
