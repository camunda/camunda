/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import java.time.Instant;

/** Entity mapping for the TOKEN_EXCHANGE_AUDIT table. */
public class TokenExchangeAuditEntity {

  private String exchangeId;
  private String subjectPrincipalId;
  private String actorPrincipalId;
  private String targetAudience;
  private String grantedScopes;
  private Instant exchangeTime;
  private Instant expiryTime;
  private String exchangeStatus;
  private String idpType;
  private String tenantId;

  public String getExchangeId() {
    return exchangeId;
  }

  public void setExchangeId(final String exchangeId) {
    this.exchangeId = exchangeId;
  }

  public String getSubjectPrincipalId() {
    return subjectPrincipalId;
  }

  public void setSubjectPrincipalId(final String subjectPrincipalId) {
    this.subjectPrincipalId = subjectPrincipalId;
  }

  public String getActorPrincipalId() {
    return actorPrincipalId;
  }

  public void setActorPrincipalId(final String actorPrincipalId) {
    this.actorPrincipalId = actorPrincipalId;
  }

  public String getTargetAudience() {
    return targetAudience;
  }

  public void setTargetAudience(final String targetAudience) {
    this.targetAudience = targetAudience;
  }

  public String getGrantedScopes() {
    return grantedScopes;
  }

  public void setGrantedScopes(final String grantedScopes) {
    this.grantedScopes = grantedScopes;
  }

  public Instant getExchangeTime() {
    return exchangeTime;
  }

  public void setExchangeTime(final Instant exchangeTime) {
    this.exchangeTime = exchangeTime;
  }

  public Instant getExpiryTime() {
    return expiryTime;
  }

  public void setExpiryTime(final Instant expiryTime) {
    this.expiryTime = expiryTime;
  }

  public String getExchangeStatus() {
    return exchangeStatus;
  }

  public void setExchangeStatus(final String exchangeStatus) {
    this.exchangeStatus = exchangeStatus;
  }

  public String getIdpType() {
    return idpType;
  }

  public void setIdpType(final String idpType) {
    this.idpType = idpType;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }
}
