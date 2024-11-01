/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import java.util.Objects;

public class IdentityServiceCfg {

  private boolean enabled = false;
  private long tenantCacheTtl = 5000;
  private long tenantCacheSize = 200;
  private int tenantRequestCapacity = 300;
  private long tenantRequestTimeout = 1000;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public long getTenantCacheTtl() {
    return tenantCacheTtl;
  }

  public void setTenantCacheTtl(final long tenantCacheTtl) {
    this.tenantCacheTtl = tenantCacheTtl;
  }

  public long getTenantCacheSize() {
    return tenantCacheSize;
  }

  public void setTenantCacheSize(final long tenantCacheSize) {
    this.tenantCacheSize = tenantCacheSize;
  }

  public int getTenantRequestCapacity() {
    return tenantRequestCapacity;
  }

  public void setTenantRequestCapacity(final int tenantRequestCapacity) {
    this.tenantRequestCapacity = tenantRequestCapacity;
  }

  public long getTenantRequestTimeout() {
    return tenantRequestTimeout;
  }

  public void setTenantRequestTimeout(final long tenantRequestTimeout) {
    this.tenantRequestTimeout = tenantRequestTimeout;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        enabled, tenantCacheTtl, tenantCacheSize, tenantRequestCapacity, tenantRequestTimeout);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IdentityServiceCfg that = (IdentityServiceCfg) o;
    return enabled == that.enabled
        && tenantCacheTtl == that.tenantCacheTtl
        && tenantCacheSize == that.tenantCacheSize
        && tenantRequestCapacity == that.tenantRequestCapacity
        && tenantRequestTimeout == that.tenantRequestTimeout;
  }

  @Override
  public String toString() {
    return "IdentityRequestCfg{"
        + "enabled="
        + enabled
        + ", tenantCacheTtl="
        + tenantCacheTtl
        + ", tenantCacheSize="
        + tenantCacheSize
        + ", tenantRequestCapacity="
        + tenantRequestCapacity
        + ", tenantRequestTimeout="
        + tenantRequestTimeout
        + '}';
  }
}
