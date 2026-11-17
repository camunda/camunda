/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.dto.optimize.query.WebSessionDto;
import io.camunda.optimize.service.db.repository.PersistentWebSessionRepository;
import io.camunda.security.api.model.session.PersistentSession;
import io.camunda.security.core.port.out.SessionStorePort;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Persists the server-side sessions of CSL's stateful OIDC webapp chain in Optimize's own database,
 * replacing the stateless JWT cookie (<a
 * href="https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md">ADR-0038</a>).
 * A shared store rather than in-memory sessions is what keeps Optimize scalable without a sticky
 * load balancer, and what makes logout able to invalidate a session cluster-wide.
 *
 * <p>OC's adapter cannot be reused: it lives in a module Optimize does not depend on and is bound
 * to that host's search clients.
 *
 * <p>Database-agnostic: it talks only to {@link PersistentWebSessionRepository}, whose
 * Elasticsearch and OpenSearch implementations are selected by the configured database. Active by
 * default since {@code optimize.security.csl.enabled} defaults to {@code true}
 * (camunda/camunda#58483); an operator can still opt back into the legacy stack with {@code
 * optimize.security.csl.enabled=false} through 8.10 (camunda/camunda#58484). CSL routes sessions
 * here only while {@code camunda.security.session.persistent.enabled=true}, which {@link
 * OptimizeSecurityConfigCompatibilityPostProcessor} sets for both editions.
 *
 * <p>A failed {@link #upsert(PersistentSession)} is logged and swallowed. Spring Session saves the
 * session while the response is being committed, so letting a storage failure through would turn a
 * database blip into failed requests. The reads stay strict: a session that cannot be loaded must
 * not be mistaken for one that does not exist.
 */
@Component
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OptimizeSessionStoreAdapter implements SessionStorePort {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeSessionStoreAdapter.class);

  private final PersistentWebSessionRepository repository;

  public OptimizeSessionStoreAdapter(final PersistentWebSessionRepository repository) {
    this.repository = repository;
  }

  @Override
  public PersistentSession get(final String sessionId) {
    return repository
        .get(sessionId)
        .map(OptimizeSessionStoreAdapter::toPersistentSession)
        .orElse(null);
  }

  @Override
  public void upsert(final PersistentSession session) {
    try {
      repository.upsert(toDto(session));
    } catch (final RuntimeException e) {
      // The session stays valid on the instance that handled the request, but it is not shared and
      // it does not survive a restart, so the user may have to log in again.
      LOG.warn("Could not save web session {} to the database.", session.id(), e);
    }
  }

  @Override
  public void delete(final String sessionId) {
    repository.delete(sessionId);
  }

  @Override
  public List<PersistentSession> getAll() {
    return repository.getAll().stream()
        .map(OptimizeSessionStoreAdapter::toPersistentSession)
        .toList();
  }

  private static PersistentSession toPersistentSession(final WebSessionDto dto) {
    return new PersistentSession(
        dto.getId(),
        dto.getCreationTime(),
        dto.getLastAccessedTime(),
        dto.getMaxInactiveIntervalInSeconds(),
        dto.getAttributes() != null ? dto.getAttributes() : Map.of());
  }

  private static WebSessionDto toDto(final PersistentSession session) {
    return new WebSessionDto(
        session.id(),
        session.creationTime(),
        session.lastAccessedTime(),
        session.maxInactiveIntervalInSeconds(),
        session.attributes());
  }
}
