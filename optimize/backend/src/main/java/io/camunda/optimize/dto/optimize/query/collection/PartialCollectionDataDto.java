/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PartialCollectionDataDto {

  protected Object configuration;

  public PartialCollectionDataDto() {}

  public Object getConfiguration() {
    return configuration;
  }

  public void setConfiguration(final Object configuration) {
    this.configuration = configuration;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PartialCollectionDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $configuration = getConfiguration();
    result = result * PRIME + ($configuration == null ? 43 : $configuration.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PartialCollectionDataDto)) {
      return false;
    }
    final PartialCollectionDataDto other = (PartialCollectionDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$configuration = getConfiguration();
    final Object other$configuration = other.getConfiguration();
    if (this$configuration == null
        ? other$configuration != null
        : !this$configuration.equals(other$configuration)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "PartialCollectionDataDto(configuration=" + getConfiguration() + ")";
  }
}
