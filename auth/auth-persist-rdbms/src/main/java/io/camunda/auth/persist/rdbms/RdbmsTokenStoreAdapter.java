/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import io.camunda.auth.domain.model.TokenMetadata;
import io.camunda.auth.domain.model.TokenMetadata.ExchangeStatus;
import io.camunda.auth.domain.port.outbound.TokenStorePort;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link TokenStorePort} using MyBatis. */
public class RdbmsTokenStoreAdapter implements TokenStorePort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsTokenStoreAdapter.class);

  private final TokenExchangeAuditMapper mapper;

  public RdbmsTokenStoreAdapter(final TokenExchangeAuditMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void store(final TokenMetadata metadata) {
    LOG.debug("Storing token exchange audit record with exchangeId={}", metadata.exchangeId());
    final TokenExchangeAuditEntity entity = toEntity(metadata);
    mapper.insert(entity);
  }

  @Override
  public Optional<TokenMetadata> findByExchangeId(final String exchangeId) {
    LOG.debug("Finding token exchange audit record by exchangeId={}", exchangeId);
    final TokenExchangeAuditEntity entity = mapper.findByExchangeId(exchangeId);
    return Optional.ofNullable(entity).map(RdbmsTokenStoreAdapter::toDomain);
  }

  @Override
  public List<TokenMetadata> findBySubjectPrincipalId(
      final String subjectPrincipalId, final Instant from, final Instant to) {
    LOG.debug(
        "Finding token exchange audit records for subjectPrincipalId={} between {} and {}",
        subjectPrincipalId,
        from,
        to);
    final List<TokenExchangeAuditEntity> entities =
        mapper.findBySubjectPrincipalId(subjectPrincipalId, from, to);
    return entities.stream().map(RdbmsTokenStoreAdapter::toDomain).collect(Collectors.toList());
  }

  private static TokenExchangeAuditEntity toEntity(final TokenMetadata metadata) {
    final TokenExchangeAuditEntity entity = new TokenExchangeAuditEntity();
    entity.setExchangeId(metadata.exchangeId());
    entity.setSubjectPrincipalId(metadata.subjectPrincipalId());
    entity.setActorPrincipalId(metadata.actorPrincipalId());
    entity.setTargetAudience(metadata.targetAudience());
    entity.setGrantedScopes(
        metadata.grantedScopes() != null && !metadata.grantedScopes().isEmpty()
            ? String.join(",", metadata.grantedScopes())
            : null);
    entity.setExchangeTime(metadata.exchangeTime());
    entity.setExpiryTime(metadata.expiryTime());
    entity.setExchangeStatus(
        metadata.exchangeStatus() != null ? metadata.exchangeStatus().name() : null);
    entity.setTenantId(metadata.tenantId());
    return entity;
  }

  private static TokenMetadata toDomain(final TokenExchangeAuditEntity entity) {
    final Set<String> scopes = parseScopes(entity.getGrantedScopes());
    return TokenMetadata.builder()
        .exchangeId(entity.getExchangeId())
        .subjectPrincipalId(entity.getSubjectPrincipalId())
        .actorPrincipalId(entity.getActorPrincipalId())
        .targetAudience(entity.getTargetAudience())
        .grantedScopes(scopes)
        .exchangeTime(entity.getExchangeTime())
        .expiryTime(entity.getExpiryTime())
        .exchangeStatus(
            entity.getExchangeStatus() != null
                ? ExchangeStatus.valueOf(entity.getExchangeStatus())
                : null)
        .tenantId(entity.getTenantId())
        .build();
  }

  private static Set<String> parseScopes(final String grantedScopes) {
    if (grantedScopes == null || grantedScopes.isBlank()) {
      return Collections.emptySet();
    }
    return Arrays.stream(grantedScopes.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
