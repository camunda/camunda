/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query;

import java.util.Objects;

public class IdResponseDto {

  protected String id;

  public IdResponseDto(final String id) {
    this.id = id;
  }

  protected IdResponseDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof IdResponseDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IdResponseDto that = (IdResponseDto) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public String toString() {
    return "IdResponseDto(id=" + getId() + ")";
  }
}
