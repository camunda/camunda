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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
