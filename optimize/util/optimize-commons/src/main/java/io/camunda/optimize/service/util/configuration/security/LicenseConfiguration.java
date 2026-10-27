/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final LicenseConfiguration that = (LicenseConfiguration) o;
    return enterprise == that.enterprise;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(enterprise);
  }

  @Override
  public String toString() {
    return "LicenseConfiguration(enterprise=" + isEnterprise() + ")";
  }
}
