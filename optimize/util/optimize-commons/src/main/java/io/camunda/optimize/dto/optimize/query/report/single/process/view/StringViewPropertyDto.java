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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final StringViewPropertyDto that = (StringViewPropertyDto) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  @Override
  public String toString() {
    return getId();
  }
}
