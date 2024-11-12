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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String includeUserMetaData = "includeUserMetaData";
    public static final String collectionRoleCleanupEnabled = "collectionRoleCleanupEnabled";
    public static final String cronTrigger = "cronTrigger";
    public static final String maxPageSize = "maxPageSize";
    public static final String maxEntryLimit = "maxEntryLimit";
  }
}
