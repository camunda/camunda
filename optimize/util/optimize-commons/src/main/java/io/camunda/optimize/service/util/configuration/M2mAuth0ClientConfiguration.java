/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final M2mAuth0ClientConfiguration that = (M2mAuth0ClientConfiguration) o;
    return Objects.equals(m2mClientId, that.m2mClientId)
        && Objects.equals(m2mClientSecret, that.m2mClientSecret);
  }

  @Override
  public int hashCode() {
    return Objects.hash(m2mClientId, m2mClientSecret);
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
