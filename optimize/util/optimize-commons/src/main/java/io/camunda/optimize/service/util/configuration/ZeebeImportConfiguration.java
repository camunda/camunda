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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
