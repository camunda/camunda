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
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof StringViewPropertyDto)) {
      return false;
    }
    final StringViewPropertyDto other = (StringViewPropertyDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return getId();
  }
}
