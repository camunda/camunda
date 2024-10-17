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
public class PartialCollectionDefinitionRequestDto {

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

  public PartialCollectionDefinitionRequestDto() {}

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $data = getData();
    result = result * PRIME + ($data == null ? 43 : $data.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PartialCollectionDefinitionRequestDto)) {
      return false;
    }
    final PartialCollectionDefinitionRequestDto other = (PartialCollectionDefinitionRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$data = getData();
    final Object other$data = other.getData();
    if (this$data == null ? other$data != null : !this$data.equals(other$data)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "PartialCollectionDefinitionRequestDto(name=" + getName() + ", data=" + getData() + ")";
  }
}
