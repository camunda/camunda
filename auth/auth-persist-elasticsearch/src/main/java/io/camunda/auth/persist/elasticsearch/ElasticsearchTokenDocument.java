/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.elasticsearch;

import io.camunda.auth.domain.model.TokenMetadata;
import io.camunda.auth.domain.model.TokenMetadata.ExchangeStatus;
import java.time.Instant;
import java.util.Set;

/**
 * Document representation for Elasticsearch storage of token exchange audit records.
 *
 * @param exchangeId unique identifier for this token exchange
 * @param subjectPrincipalId the principal ID of the subject token
 * @param actorPrincipalId the principal ID of the actor performing the exchange
 * @param targetAudience the target audience/service
 * @param grantedScopes the scopes granted in the exchanged token
 * @param exchangeTime when the exchange occurred (epoch millis)
 * @param expiryTime when the exchanged token expires (epoch millis)
 * @param exchangeStatus the outcome of the exchange as a string
 * @param idpType the identity provider type as a string
 * @param tenantId the tenant context
 */
public record ElasticsearchTokenDocument(
    String exchangeId,
    String subjectPrincipalId,
    String actorPrincipalId,
    String targetAudience,
    Set<String> grantedScopes,
    long exchangeTime,
    long expiryTime,
    String exchangeStatus,
    String idpType,
    String tenantId) {

  /**
   * Creates an Elasticsearch document from a domain {@link TokenMetadata} instance.
   *
   * @param metadata the domain token metadata
   * @return the Elasticsearch document representation
   */
  public static ElasticsearchTokenDocument fromDomain(final TokenMetadata metadata) {
    return new ElasticsearchTokenDocument(
        metadata.exchangeId(),
        metadata.subjectPrincipalId(),
        metadata.actorPrincipalId(),
        metadata.targetAudience(),
        metadata.grantedScopes(),
        metadata.exchangeTime() != null ? metadata.exchangeTime().toEpochMilli() : 0L,
        metadata.expiryTime() != null ? metadata.expiryTime().toEpochMilli() : 0L,
        metadata.exchangeStatus() != null ? metadata.exchangeStatus().name() : null,
        null,
        metadata.tenantId());
  }

  /**
   * Converts this Elasticsearch document back to a domain {@link TokenMetadata} instance.
   *
   * @return the domain token metadata
   */
  public TokenMetadata toDomain() {
    return TokenMetadata.builder()
        .exchangeId(exchangeId)
        .subjectPrincipalId(subjectPrincipalId)
        .actorPrincipalId(actorPrincipalId)
        .targetAudience(targetAudience)
        .grantedScopes(grantedScopes)
        .exchangeTime(exchangeTime > 0 ? Instant.ofEpochMilli(exchangeTime) : null)
        .expiryTime(expiryTime > 0 ? Instant.ofEpochMilli(expiryTime) : null)
        .exchangeStatus(exchangeStatus != null ? ExchangeStatus.valueOf(exchangeStatus) : null)
        .tenantId(tenantId)
        .build();
  }
}
