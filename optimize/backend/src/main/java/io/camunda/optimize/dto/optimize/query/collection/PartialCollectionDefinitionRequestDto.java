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
public class PartialCollectionDefinitionRequestDto {

  protected String ownerId;
  protected String name;
  protected PartialCollectionDataDto data;

  public PartialCollectionDefinitionRequestDto(final String name) {
    this.name = name;
  }

  public PartialCollectionDefinitionRequestDto(
      final String name, final PartialCollectionDataDto data) {
    this.name = name;
    this.data = data;
  }

  public PartialCollectionDefinitionRequestDto(final String name, final String ownerId) {
    this.name = name;
    this.ownerId = ownerId;
  }

  public PartialCollectionDefinitionRequestDto() {}

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(final String ownerId) {
    this.ownerId = ownerId;
  }

  public PartialCollectionDataDto getData() {
    return data;
  }

  public void setData(final PartialCollectionDataDto data) {
    this.data = data;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PartialCollectionDefinitionRequestDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PartialCollectionDefinitionRequestDto that = (PartialCollectionDefinitionRequestDto) o;
    return Objects.equals(ownerId, that.ownerId)
        && Objects.equals(name, that.name)
        && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ownerId, name, data);
  }

  @Override
  public String toString() {
    return "PartialCollectionDefinitionRequestDto("
        + "ownerId="
        + getOwnerId()
        + ", name="
        + getName()
        + ", data="
        + getData()
        + ")";
  }
}
