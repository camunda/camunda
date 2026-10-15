/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query;

/** POC: persisted claim-based mapping rule. */
public class AccessMappingRuleDto {

  private String id;
  private String claimName;
  private String claimValue;

  public AccessMappingRuleDto() {}

  public AccessMappingRuleDto(final String id, final String claimName, final String claimValue) {
    this.id = id;
    this.claimName = claimName;
    this.claimValue = claimValue;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getClaimName() {
    return claimName;
  }

  public void setClaimName(final String claimName) {
    this.claimName = claimName;
  }

  public String getClaimValue() {
    return claimValue;
  }

  public void setClaimValue(final String claimValue) {
    this.claimValue = claimValue;
  }
}
