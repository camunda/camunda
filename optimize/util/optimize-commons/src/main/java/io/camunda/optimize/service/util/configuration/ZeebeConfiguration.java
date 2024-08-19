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
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEnabled() ? 79 : 97);
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    result = result * PRIME + getPartitionCount();
    result = result * PRIME + getMaxImportPageSize();
    result = result * PRIME + (isIncludeObjectVariableValue() ? 79 : 97);
    final Object $importConfig = getImportConfig();
    result = result * PRIME + ($importConfig == null ? 43 : $importConfig.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ZeebeConfiguration)) {
      return false;
    }
    final ZeebeConfiguration other = (ZeebeConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isEnabled() != other.isEnabled()) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    if (getPartitionCount() != other.getPartitionCount()) {
      return false;
    }
    if (getMaxImportPageSize() != other.getMaxImportPageSize()) {
      return false;
    }
    if (isIncludeObjectVariableValue() != other.isIncludeObjectVariableValue()) {
      return false;
    }
    final Object this$importConfig = getImportConfig();
    final Object other$importConfig = other.getImportConfig();
    if (this$importConfig == null
        ? other$importConfig != null
        : !this$importConfig.equals(other$importConfig)) {
      return false;
    }
    return true;
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
