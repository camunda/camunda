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
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import io.camunda.security.api.model.session.PersistentSession;
import io.camunda.security.core.port.out.SessionStorePort;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
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
 * <p>Active only under Elasticsearch and when {@code optimize.security.csl.enabled=true}. CSL
 * routes sessions here only while {@code camunda.security.session.persistent.enabled=true}, which
 * {@link OptimizeSecurityConfigCompatibilityPostProcessor} sets for the Elasticsearch edition;
 * OpenSearch keeps CSL's in-memory sessions until the OpenSearch store lands.
 */
@Component
@Conditional(ElasticSearchCondition.class)
@ConditionalOnProperty(name = "optimize.security.csl.enabled", havingValue = "true")
public class OptimizeSessionStoreAdapter implements SessionStorePort {

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
    repository.upsert(toDto(session));
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
