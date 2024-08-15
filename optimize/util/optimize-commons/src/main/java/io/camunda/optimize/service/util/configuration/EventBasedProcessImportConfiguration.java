/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

public class EventBasedProcessImportConfiguration {

  private int indexMaintenanceIntervalInSec;

  protected EventBasedProcessImportConfiguration() {}

  public int getIndexMaintenanceIntervalInSec() {
    return indexMaintenanceIntervalInSec;
  }

  public void setIndexMaintenanceIntervalInSec(final int indexMaintenanceIntervalInSec) {
    this.indexMaintenanceIntervalInSec = indexMaintenanceIntervalInSec;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventBasedProcessImportConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + getIndexMaintenanceIntervalInSec();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventBasedProcessImportConfiguration)) {
      return false;
    }
    final EventBasedProcessImportConfiguration other = (EventBasedProcessImportConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getIndexMaintenanceIntervalInSec() != other.getIndexMaintenanceIntervalInSec()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventBasedProcessImportConfiguration(indexMaintenanceIntervalInSec="
        + getIndexMaintenanceIntervalInSec()
        + ")";
  }
}
