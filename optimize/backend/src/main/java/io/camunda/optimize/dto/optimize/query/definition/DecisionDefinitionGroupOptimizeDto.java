/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.definition;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DecisionDefinitionGroupOptimizeDto {

  private String key;
  private List<DecisionDefinitionOptimizeDto> versions = new ArrayList<>();

  public DecisionDefinitionGroupOptimizeDto() {}

  public void sort() {
    try {
      versions.sort(
          Comparator.comparing(
                  DecisionDefinitionOptimizeDto::getVersion, Comparator.comparing(Long::valueOf))
              .reversed());
    } catch (final NumberFormatException exception) {
      throw new OptimizeRuntimeException(
          "Error while trying to parse version numbers for sorting decision definition groups: "
              + versions);
    }
  }

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }

  public List<DecisionDefinitionOptimizeDto> getVersions() {
    return versions;
  }

  public void setVersions(final List<DecisionDefinitionOptimizeDto> versions) {
    this.versions = versions;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DecisionDefinitionGroupOptimizeDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $key = getKey();
    result = result * PRIME + ($key == null ? 43 : $key.hashCode());
    final Object $versions = getVersions();
    result = result * PRIME + ($versions == null ? 43 : $versions.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DecisionDefinitionGroupOptimizeDto)) {
      return false;
    }
    final DecisionDefinitionGroupOptimizeDto other = (DecisionDefinitionGroupOptimizeDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$key = getKey();
    final Object other$key = other.getKey();
    if (this$key == null ? other$key != null : !this$key.equals(other$key)) {
      return false;
    }
    final Object this$versions = getVersions();
    final Object other$versions = other.getVersions();
    if (this$versions == null ? other$versions != null : !this$versions.equals(other$versions)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DecisionDefinitionGroupOptimizeDto(key="
        + getKey()
        + ", versions="
        + getVersions()
        + ")";
  }
}
