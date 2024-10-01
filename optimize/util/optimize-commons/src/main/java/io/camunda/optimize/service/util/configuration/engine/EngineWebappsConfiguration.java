/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.engine;

import io.camunda.optimize.service.util.configuration.ConfigurationUtil;

public class EngineWebappsConfiguration {

  private String endpoint;
  private boolean enabled;

  public EngineWebappsConfiguration() {}

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(final String endpoint) {
    this.endpoint = ConfigurationUtil.cutTrailingSlash(endpoint);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EngineWebappsConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $endpoint = getEndpoint();
    result = result * PRIME + ($endpoint == null ? 43 : $endpoint.hashCode());
    result = result * PRIME + (isEnabled() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EngineWebappsConfiguration)) {
      return false;
    }
    final EngineWebappsConfiguration other = (EngineWebappsConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$endpoint = getEndpoint();
    final Object other$endpoint = other.getEndpoint();
    if (this$endpoint == null ? other$endpoint != null : !this$endpoint.equals(other$endpoint)) {
      return false;
    }
    if (isEnabled() != other.isEnabled()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EngineWebappsConfiguration(endpoint="
        + getEndpoint()
        + ", enabled="
        + isEnabled()
        + ")";
  }
}
