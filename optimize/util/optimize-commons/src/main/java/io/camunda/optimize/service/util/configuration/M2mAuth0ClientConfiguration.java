/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

public class M2mAuth0ClientConfiguration {

  private String m2mClientId;
  private String m2mClientSecret;

  public M2mAuth0ClientConfiguration() {}

  public String getM2mClientId() {
    return m2mClientId;
  }

  public void setM2mClientId(final String m2mClientId) {
    this.m2mClientId = m2mClientId;
  }

  public String getM2mClientSecret() {
    return m2mClientSecret;
  }

  public void setM2mClientSecret(final String m2mClientSecret) {
    this.m2mClientSecret = m2mClientSecret;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof M2mAuth0ClientConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $m2mClientId = getM2mClientId();
    result = result * PRIME + ($m2mClientId == null ? 43 : $m2mClientId.hashCode());
    final Object $m2mClientSecret = getM2mClientSecret();
    result = result * PRIME + ($m2mClientSecret == null ? 43 : $m2mClientSecret.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof M2mAuth0ClientConfiguration)) {
      return false;
    }
    final M2mAuth0ClientConfiguration other = (M2mAuth0ClientConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$m2mClientId = getM2mClientId();
    final Object other$m2mClientId = other.getM2mClientId();
    if (this$m2mClientId == null
        ? other$m2mClientId != null
        : !this$m2mClientId.equals(other$m2mClientId)) {
      return false;
    }
    final Object this$m2mClientSecret = getM2mClientSecret();
    final Object other$m2mClientSecret = other.getM2mClientSecret();
    if (this$m2mClientSecret == null
        ? other$m2mClientSecret != null
        : !this$m2mClientSecret.equals(other$m2mClientSecret)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "M2mAuth0ClientConfiguration(m2mClientId="
        + getM2mClientId()
        + ", m2mClientSecret="
        + getM2mClientSecret()
        + ")";
  }
}
