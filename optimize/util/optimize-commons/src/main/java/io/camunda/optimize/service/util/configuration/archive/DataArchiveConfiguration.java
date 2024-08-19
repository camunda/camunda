/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.archive;

public class DataArchiveConfiguration {

  private boolean enabled;
  private int archiveIntervalInMins;

  public DataArchiveConfiguration() {}

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getArchiveIntervalInMins() {
    return archiveIntervalInMins;
  }

  public void setArchiveIntervalInMins(final int archiveIntervalInMins) {
    this.archiveIntervalInMins = archiveIntervalInMins;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DataArchiveConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEnabled() ? 79 : 97);
    result = result * PRIME + getArchiveIntervalInMins();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DataArchiveConfiguration)) {
      return false;
    }
    final DataArchiveConfiguration other = (DataArchiveConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isEnabled() != other.isEnabled()) {
      return false;
    }
    if (getArchiveIntervalInMins() != other.getArchiveIntervalInMins()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DataArchiveConfiguration(enabled="
        + isEnabled()
        + ", archiveIntervalInMins="
        + getArchiveIntervalInMins()
        + ")";
  }
}
