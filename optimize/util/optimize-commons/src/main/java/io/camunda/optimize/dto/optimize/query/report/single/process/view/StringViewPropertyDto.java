/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.view;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

public class StringViewPropertyDto implements TypedViewPropertyDto {

  private final String id;

  public StringViewPropertyDto(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof StringViewPropertyDto)) {
      return false;
    }
    final StringViewPropertyDto stringViewPropertyDto = (StringViewPropertyDto) o;
    return Objects.equals(getId(), stringViewPropertyDto.getId());
  }

  protected boolean canEqual(final Object other) {
    return other instanceof StringViewPropertyDto;
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
    return getId();
  }
}
