/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.definition;

import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ProcessDefinitionGroupOptimizeDto {

  private String key;
  private List<ProcessDefinitionOptimizeDto> versions = new ArrayList<>();

  public ProcessDefinitionGroupOptimizeDto() {}

  public void sort() {
    try {
      versions.sort(
          Comparator.comparing(
                  ProcessDefinitionOptimizeDto::getVersion, Comparator.comparing(Long::valueOf))
              .reversed());
    } catch (final NumberFormatException exception) {
      throw new OptimizeRuntimeException(
          "Error while trying to parse version numbers for sorting process definition groups: "
              + versions);
    }
  }

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }

  public List<ProcessDefinitionOptimizeDto> getVersions() {
    return versions;
  }

  public void setVersions(final List<ProcessDefinitionOptimizeDto> versions) {
    this.versions = versions;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessDefinitionGroupOptimizeDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessDefinitionGroupOptimizeDto that = (ProcessDefinitionGroupOptimizeDto) o;
    return Objects.equals(key, that.key) && Objects.equals(versions, that.versions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, versions);
  }

  @Override
  public String toString() {
    return "ProcessDefinitionGroupOptimizeDto(key="
        + getKey()
        + ", versions="
        + getVersions()
        + ")";
  }
}
