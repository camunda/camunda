/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Metadata about an issued or exchanged token, used for audit logging and cache management.
 *
 * @param exchangeId unique identifier for this token exchange operation
 * @param subjectPrincipalId the principal ID of the subject token
 * @param actorPrincipalId the principal ID of the actor (the service performing the exchange)
 * @param targetAudience the target audience/service the token was exchanged for
 * @param grantedScopes the scopes granted in the exchanged token
 * @param exchangeTime when the exchange occurred
 * @param expiryTime when the exchanged token expires
 * @param exchangeStatus the outcome of the exchange
 * @param tenantId the tenant context, if applicable
 * @param additionalClaims any extra claims from the token
 */
public record TokenMetadata(
    String exchangeId,
    String subjectPrincipalId,
    String actorPrincipalId,
    String targetAudience,
    Set<String> grantedScopes,
    Instant exchangeTime,
    Instant expiryTime,
    ExchangeStatus exchangeStatus,
    String tenantId,
    Map<String, Object> additionalClaims) {

  public TokenMetadata {
    Objects.requireNonNull(exchangeId, "exchangeId must not be null");
    Objects.requireNonNull(exchangeTime, "exchangeTime must not be null");
    Objects.requireNonNull(exchangeStatus, "exchangeStatus must not be null");
    grantedScopes = grantedScopes != null ? Set.copyOf(grantedScopes) : Set.of();
    additionalClaims = additionalClaims != null ? Map.copyOf(additionalClaims) : Map.of();
  }

  public enum ExchangeStatus {
    SUCCESS,
    FAILED,
    EXPIRED
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String exchangeId;
    private String subjectPrincipalId;
    private String actorPrincipalId;
    private String targetAudience;
    private Set<String> grantedScopes;
    private Instant exchangeTime;
    private Instant expiryTime;
    private ExchangeStatus exchangeStatus;
    private String tenantId;
    private Map<String, Object> additionalClaims;

    private Builder() {}

    public Builder exchangeId(final String exchangeId) {
      this.exchangeId = exchangeId;
      return this;
    }

    public Builder subjectPrincipalId(final String subjectPrincipalId) {
      this.subjectPrincipalId = subjectPrincipalId;
      return this;
    }

    public Builder actorPrincipalId(final String actorPrincipalId) {
      this.actorPrincipalId = actorPrincipalId;
      return this;
    }

    public Builder targetAudience(final String targetAudience) {
      this.targetAudience = targetAudience;
      return this;
    }

    public Builder grantedScopes(final Set<String> grantedScopes) {
      this.grantedScopes = grantedScopes;
      return this;
    }

    public Builder exchangeTime(final Instant exchangeTime) {
      this.exchangeTime = exchangeTime;
      return this;
    }

    public Builder expiryTime(final Instant expiryTime) {
      this.expiryTime = expiryTime;
      return this;
    }

    public Builder exchangeStatus(final ExchangeStatus exchangeStatus) {
      this.exchangeStatus = exchangeStatus;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder additionalClaims(final Map<String, Object> additionalClaims) {
      this.additionalClaims = additionalClaims;
      return this;
    }

    public TokenMetadata build() {
      return new TokenMetadata(
          exchangeId,
          subjectPrincipalId,
          actorPrincipalId,
          targetAudience,
          grantedScopes,
          exchangeTime,
          expiryTime,
          exchangeStatus,
          tenantId,
          additionalClaims);
    }
  }
}
