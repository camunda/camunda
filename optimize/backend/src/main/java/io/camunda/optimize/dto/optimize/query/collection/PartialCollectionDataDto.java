/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PartialCollectionDataDto that = (PartialCollectionDataDto) o;
    return Objects.equals(configuration, that.configuration);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(configuration);
  }

  @Override
  public String toString() {
    return "PartialCollectionDataDto(configuration=" + getConfiguration() + ")";
  }
}
