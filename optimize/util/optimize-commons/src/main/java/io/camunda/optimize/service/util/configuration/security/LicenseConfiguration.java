/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.security;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LicenseConfiguration {

  @JsonProperty("enterprise")
  private boolean enterprise;

  public LicenseConfiguration() {}

  public boolean isEnterprise() {
    return enterprise;
  }

  @JsonProperty("enterprise")
  public void setEnterprise(final boolean enterprise) {
    this.enterprise = enterprise;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof LicenseConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEnterprise() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof LicenseConfiguration)) {
      return false;
    }
    final LicenseConfiguration other = (LicenseConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isEnterprise() != other.isEnterprise()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "LicenseConfiguration(enterprise=" + isEnterprise() + ")";
  }
}
