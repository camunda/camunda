/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.api.model.session.PersistentSession;
import io.camunda.security.core.port.out.SessionStorePort;
import java.util.List;

/**
 * SPIKE (ADR-0036): Optimize's {@link SessionStorePort} adapter, backed by Optimize's existing
 * Elasticsearch/OpenSearch store. This is the piece that replaces the stateless cookie: the CSL
 * webapp chain persists the server-side session here, so scaling stays affinity-free and logout is
 * a real store delete.
 *
 * <p>STUB: the actual read/write to an Optimize session index is not implemented in this spike. The
 * shape mirrors OC's {@code SessionStoreAdapter}, which wraps a {@code PersistentWebSessionClient}
 * over the search-client layer. For Optimize the backing store is the same Elasticsearch cluster
 * Optimize already runs (and already uses today for {@code TerminatedSessionService}), so this
 * adapter should write a small session document to a dedicated Optimize index. The recommended
 * implementation reuses OC's resilience pattern (retry transient search failures on upsert).
 *
 * <p>Implementation task (see SPIKE-NOTES.md):
 *
 * <ul>
 *   <li>define an Optimize session index + descriptor,
 *   <li>map {@link PersistentSession} to/from the index document (id, timestamps, attributes),
 *   <li>wire this adapter as the {@code sessionStorePort} bean and import CSL's {@code
 *       WebSessionConfiguration} (which owns the repository/mapper/deletion-scheduler beans),
 *   <li>set {@code camunda.security.session.persistent.enabled=true}.
 * </ul>
 */
public final class OptimizeSessionStoreAdapter implements SessionStorePort {

  // TODO(spike): inject Optimize's Elasticsearch/OpenSearch session client here.

  @Override
  public PersistentSession get(final String sessionId) {
    throw new UnsupportedOperationException(
        "SPIKE stub: back with Optimize's Elasticsearch session index");
  }

  @Override
  public void upsert(final PersistentSession session) {
    throw new UnsupportedOperationException(
        "SPIKE stub: back with Optimize's Elasticsearch session index");
  }

  @Override
  public void delete(final String sessionId) {
    throw new UnsupportedOperationException(
        "SPIKE stub: back with Optimize's Elasticsearch session index");
  }

  @Override
  public List<PersistentSession> getAll() {
    throw new UnsupportedOperationException(
        "SPIKE stub: back with Optimize's Elasticsearch session index");
  }
}
