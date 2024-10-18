/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

public class ZeebeConfiguration {

  private boolean enabled;
  private String name;
  private int partitionCount;
  private int maxImportPageSize;
  private boolean includeObjectVariableValue;
  private ZeebeImportConfiguration importConfig;

  public ZeebeConfiguration(
      final boolean enabled,
      final String name,
      final int partitionCount,
      final int maxImportPageSize,
      final boolean includeObjectVariableValue,
      final ZeebeImportConfiguration importConfig) {
    this.enabled = enabled;
    this.name = name;
    this.partitionCount = partitionCount;
    this.maxImportPageSize = maxImportPageSize;
    this.includeObjectVariableValue = includeObjectVariableValue;
    this.importConfig = importConfig;
  }

  protected ZeebeConfiguration() {}

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public int getPartitionCount() {
    return partitionCount;
  }

  public void setPartitionCount(final int partitionCount) {
    this.partitionCount = partitionCount;
  }

  public int getMaxImportPageSize() {
    return maxImportPageSize;
  }

  public void setMaxImportPageSize(final int maxImportPageSize) {
    this.maxImportPageSize = maxImportPageSize;
  }

  public boolean isIncludeObjectVariableValue() {
    return includeObjectVariableValue;
  }

  public void setIncludeObjectVariableValue(final boolean includeObjectVariableValue) {
    this.includeObjectVariableValue = includeObjectVariableValue;
  }

  public ZeebeImportConfiguration getImportConfig() {
    return importConfig;
  }

  public void setImportConfig(final ZeebeImportConfiguration importConfig) {
    this.importConfig = importConfig;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeConfiguration;
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
    return "ZeebeConfiguration(enabled="
        + isEnabled()
        + ", name="
        + getName()
        + ", partitionCount="
        + getPartitionCount()
        + ", maxImportPageSize="
        + getMaxImportPageSize()
        + ", includeObjectVariableValue="
        + isIncludeObjectVariableValue()
        + ", importConfig="
        + getImportConfig()
        + ")";
  }
}
