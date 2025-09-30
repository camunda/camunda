/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import java.time.OffsetDateTime;
import java.util.Objects;

public class BaseCollectionDefinitionDto<DATA_TYPE> {

  protected String id;
  protected String name;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected DATA_TYPE data;
  protected boolean automaticallyCreated = false;

  public BaseCollectionDefinitionDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

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

  public OffsetDateTime getCreated() {
    return created;
  }

  public void setCreated(final OffsetDateTime created) {
    this.created = created;
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

  public DATA_TYPE getData() {
    return data;
  }

  public void setData(final DATA_TYPE data) {
    this.data = data;
  }

  public boolean isAutomaticallyCreated() {
    return automaticallyCreated;
  }

  public void setAutomaticallyCreated(final boolean automaticallyCreated) {
    this.automaticallyCreated = automaticallyCreated;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof BaseCollectionDefinitionDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id, name, lastModified, created, owner, lastModifier, data, automaticallyCreated);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BaseCollectionDefinitionDto<?> that = (BaseCollectionDefinitionDto<?>) o;
    return automaticallyCreated == that.automaticallyCreated
        && Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(lastModified, that.lastModified)
        && Objects.equals(created, that.created)
        && Objects.equals(owner, that.owner)
        && Objects.equals(lastModifier, that.lastModifier)
        && Objects.equals(data, that.data);
  }

  @Override
  public String toString() {
    return "BaseCollectionDefinitionDto(id="
        + getId()
        + ", name="
        + getName()
        + ", lastModified="
        + getLastModified()
        + ", created="
        + getCreated()
        + ", owner="
        + getOwner()
        + ", lastModifier="
        + getLastModifier()
        + ", data="
        + getData()
        + ", automaticallyCreated="
        + isAutomaticallyCreated()
        + ")";
  }

  public enum Fields {
    id,
    name,
    lastModified,
    created,
    owner,
    lastModifier,
    data,
    automaticallyCreated
  }
}
