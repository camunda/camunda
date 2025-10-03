/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollectionDefinitionUpdateDto {

  protected String name;
  protected OffsetDateTime lastModified;
  protected String owner;
  protected String lastModifier;
  protected PartialCollectionDataDto data;

  public CollectionDefinitionUpdateDto() {}

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public OffsetDateTime getLastModified() {
    return lastModified;
  }

  public void setLastModified(final OffsetDateTime lastModified) {
    this.lastModified = lastModified;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(final String owner) {
    this.owner = owner;
  }

  public String getLastModifier() {
    return lastModifier;
  }

  public void setLastModifier(final String lastModifier) {
    this.lastModifier = lastModifier;
  }

  public PartialCollectionDataDto getData() {
    return data;
  }

  public void setData(final PartialCollectionDataDto data) {
    this.data = data;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CollectionDefinitionUpdateDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, lastModified, owner, lastModifier, data);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CollectionDefinitionUpdateDto that = (CollectionDefinitionUpdateDto) o;
    return Objects.equals(name, that.name)
        && Objects.equals(lastModified, that.lastModified)
        && Objects.equals(owner, that.owner)
        && Objects.equals(lastModifier, that.lastModifier)
        && Objects.equals(data, that.data);
  }

  @Override
  public String toString() {
    return "CollectionDefinitionUpdateDto(name="
        + getName()
        + ", lastModified="
        + getLastModified()
        + ", owner="
        + getOwner()
        + ", lastModifier="
        + getLastModifier()
        + ", data="
        + getData()
        + ")";
  }
}
