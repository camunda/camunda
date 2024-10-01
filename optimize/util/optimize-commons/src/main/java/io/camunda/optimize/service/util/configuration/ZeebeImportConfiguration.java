/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

public class ZeebeImportConfiguration {

  private int dynamicBatchSuccessAttempts;
  private int maxEmptyPagesToImport;

  public ZeebeImportConfiguration(
      final int dynamicBatchSuccessAttempts, final int maxEmptyPagesToImport) {
    this.dynamicBatchSuccessAttempts = dynamicBatchSuccessAttempts;
    this.maxEmptyPagesToImport = maxEmptyPagesToImport;
  }

  protected ZeebeImportConfiguration() {}

  public int getDynamicBatchSuccessAttempts() {
    return dynamicBatchSuccessAttempts;
  }

  public void setDynamicBatchSuccessAttempts(final int dynamicBatchSuccessAttempts) {
    this.dynamicBatchSuccessAttempts = dynamicBatchSuccessAttempts;
  }

  public int getMaxEmptyPagesToImport() {
    return maxEmptyPagesToImport;
  }

  public void setMaxEmptyPagesToImport(final int maxEmptyPagesToImport) {
    this.maxEmptyPagesToImport = maxEmptyPagesToImport;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeImportConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + getDynamicBatchSuccessAttempts();
    result = result * PRIME + getMaxEmptyPagesToImport();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ZeebeImportConfiguration)) {
      return false;
    }
    final ZeebeImportConfiguration other = (ZeebeImportConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getDynamicBatchSuccessAttempts() != other.getDynamicBatchSuccessAttempts()) {
      return false;
    }
    if (getMaxEmptyPagesToImport() != other.getMaxEmptyPagesToImport()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ZeebeImportConfiguration(dynamicBatchSuccessAttempts="
        + getDynamicBatchSuccessAttempts()
        + ", maxEmptyPagesToImport="
        + getMaxEmptyPagesToImport()
        + ")";
  }
}
