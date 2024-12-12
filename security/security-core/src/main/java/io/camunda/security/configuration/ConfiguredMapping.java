/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

public class ConfiguredMapping {

  private String claimName;
  private String claimValue;

  public ConfiguredMapping(final String claimName, final String claimValue) {
    this.claimName = claimName;
    this.claimValue = claimValue;
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
