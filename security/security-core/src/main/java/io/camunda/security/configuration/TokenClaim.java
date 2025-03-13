/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

public class TokenClaim {
  private String claim;
  private String value;

  public TokenClaim(final String claim, final String value) {
    this.claim = claim;
    this.value = value;
  }

  public String getClaim() {
    return claim;
  }

  public void setClaim(final String claim) {
    this.claim = claim;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }
}
