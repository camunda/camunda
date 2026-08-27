/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.query.SearchQueryResult;
import org.jspecify.annotations.Nullable;

/**
 * Implementations must surface infrastructure failures (e.g. a dropped connection) as a {@link
 * CamundaSearchException} with the matching {@link CamundaSearchException.Reason}, never as a bare
 * backend-specific exception — callers such as {@code SessionStoreAdapter} rely on this to tell a
 * transient failure worth retrying apart from a programming error.
 */
public interface PersistentWebSessionClient {

  @Nullable PersistentWebSessionEntity getPersistentWebSession(final String sessionId);

  void upsertPersistentWebSession(final PersistentWebSessionEntity persistentWebSessionEntity);

  void deletePersistentWebSession(final String sessionId);

  SearchQueryResult<PersistentWebSessionEntity> getAllPersistentWebSessions();
}
