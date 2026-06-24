/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportDefinitionUpdateDto {

  protected String id;
  protected String name;
  protected String description;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected String collectionId;

  public ReportDefinitionUpdateDto() {}

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

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
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

  public String getCollectionId() {
    return collectionId;
  }

  public void setCollectionId(final String collectionId) {
    this.collectionId = collectionId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ReportDefinitionUpdateDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ReportDefinitionUpdateDto that = (ReportDefinitionUpdateDto) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(description, that.description)
        && Objects.equals(lastModified, that.lastModified)
        && Objects.equals(created, that.created)
        && Objects.equals(owner, that.owner)
        && Objects.equals(lastModifier, that.lastModifier)
        && Objects.equals(collectionId, that.collectionId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id, name, description, lastModified, created, owner, lastModifier, collectionId);
  }

  @Override
  public String toString() {
    return "ReportDefinitionUpdateDto(id="
        + getId()
        + ", name="
        + getName()
        + ", description="
        + getDescription()
        + ", lastModified="
        + getLastModified()
        + ", created="
        + getCreated()
        + ", owner="
        + getOwner()
        + ", lastModifier="
        + getLastModifier()
        + ", collectionId="
        + getCollectionId()
        + ")";
  }
}
