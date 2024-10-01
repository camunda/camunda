/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.engine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.CronNormalizerUtil;
import java.util.Optional;

public abstract class IdentityCacheConfiguration {

  private boolean includeUserMetaData;
  private boolean collectionRoleCleanupEnabled;
  private String cronTrigger;
  private int maxPageSize;
  private long maxEntryLimit;

  public IdentityCacheConfiguration() {}

  public void validate() {
    if (cronTrigger == null || cronTrigger.isEmpty()) {
      throw new OptimizeConfigurationException(
          getConfigName() + ".cronTrigger must be set and not empty");
    }
  }

  @JsonIgnore
  public abstract String getConfigName();

  public boolean isIncludeUserMetaData() {
    return includeUserMetaData;
  }

  public void setIncludeUserMetaData(final boolean includeUserMetaData) {
    this.includeUserMetaData = includeUserMetaData;
  }

  public boolean isCollectionRoleCleanupEnabled() {
    return collectionRoleCleanupEnabled;
  }

  public void setCollectionRoleCleanupEnabled(final boolean collectionRoleCleanupEnabled) {
    this.collectionRoleCleanupEnabled = collectionRoleCleanupEnabled;
  }

  public String getCronTrigger() {
    return cronTrigger;
  }

  public final void setCronTrigger(final String cronTrigger) {
    this.cronTrigger =
        Optional.ofNullable(cronTrigger).map(CronNormalizerUtil::normalizeToSixParts).orElse(null);
  }

  public int getMaxPageSize() {
    return maxPageSize;
  }

  public void setMaxPageSize(final int maxPageSize) {
    this.maxPageSize = maxPageSize;
  }

  public long getMaxEntryLimit() {
    return maxEntryLimit;
  }

  public void setMaxEntryLimit(final long maxEntryLimit) {
    this.maxEntryLimit = maxEntryLimit;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof IdentityCacheConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isIncludeUserMetaData() ? 79 : 97);
    result = result * PRIME + (isCollectionRoleCleanupEnabled() ? 79 : 97);
    final Object $cronTrigger = getCronTrigger();
    result = result * PRIME + ($cronTrigger == null ? 43 : $cronTrigger.hashCode());
    result = result * PRIME + getMaxPageSize();
    final long $maxEntryLimit = getMaxEntryLimit();
    result = result * PRIME + (int) ($maxEntryLimit >>> 32 ^ $maxEntryLimit);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof IdentityCacheConfiguration)) {
      return false;
    }
    final IdentityCacheConfiguration other = (IdentityCacheConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isIncludeUserMetaData() != other.isIncludeUserMetaData()) {
      return false;
    }
    if (isCollectionRoleCleanupEnabled() != other.isCollectionRoleCleanupEnabled()) {
      return false;
    }
    final Object this$cronTrigger = getCronTrigger();
    final Object other$cronTrigger = other.getCronTrigger();
    if (this$cronTrigger == null
        ? other$cronTrigger != null
        : !this$cronTrigger.equals(other$cronTrigger)) {
      return false;
    }
    if (getMaxPageSize() != other.getMaxPageSize()) {
      return false;
    }
    if (getMaxEntryLimit() != other.getMaxEntryLimit()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "IdentityCacheConfiguration(includeUserMetaData="
        + isIncludeUserMetaData()
        + ", collectionRoleCleanupEnabled="
        + isCollectionRoleCleanupEnabled()
        + ", cronTrigger="
        + getCronTrigger()
        + ", maxPageSize="
        + getMaxPageSize()
        + ", maxEntryLimit="
        + getMaxEntryLimit()
        + ")";
  }

  public static final class Fields {

    public static final String includeUserMetaData = "includeUserMetaData";
    public static final String collectionRoleCleanupEnabled = "collectionRoleCleanupEnabled";
    public static final String cronTrigger = "cronTrigger";
    public static final String maxPageSize = "maxPageSize";
    public static final String maxEntryLimit = "maxEntryLimit";
  }
}
