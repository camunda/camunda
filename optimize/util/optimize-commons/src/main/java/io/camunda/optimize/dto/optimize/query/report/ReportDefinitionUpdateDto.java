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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $description = getDescription();
    result = result * PRIME + ($description == null ? 43 : $description.hashCode());
    final Object $lastModified = getLastModified();
    result = result * PRIME + ($lastModified == null ? 43 : $lastModified.hashCode());
    final Object $created = getCreated();
    result = result * PRIME + ($created == null ? 43 : $created.hashCode());
    final Object $owner = getOwner();
    result = result * PRIME + ($owner == null ? 43 : $owner.hashCode());
    final Object $lastModifier = getLastModifier();
    result = result * PRIME + ($lastModifier == null ? 43 : $lastModifier.hashCode());
    final Object $collectionId = getCollectionId();
    result = result * PRIME + ($collectionId == null ? 43 : $collectionId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ReportDefinitionUpdateDto)) {
      return false;
    }
    final ReportDefinitionUpdateDto other = (ReportDefinitionUpdateDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$description = getDescription();
    final Object other$description = other.getDescription();
    if (this$description == null
        ? other$description != null
        : !this$description.equals(other$description)) {
      return false;
    }
    final Object this$lastModified = getLastModified();
    final Object other$lastModified = other.getLastModified();
    if (this$lastModified == null
        ? other$lastModified != null
        : !this$lastModified.equals(other$lastModified)) {
      return false;
    }
    final Object this$created = getCreated();
    final Object other$created = other.getCreated();
    if (this$created == null ? other$created != null : !this$created.equals(other$created)) {
      return false;
    }
    final Object this$owner = getOwner();
    final Object other$owner = other.getOwner();
    if (this$owner == null ? other$owner != null : !this$owner.equals(other$owner)) {
      return false;
    }
    final Object this$lastModifier = getLastModifier();
    final Object other$lastModifier = other.getLastModifier();
    if (this$lastModifier == null
        ? other$lastModifier != null
        : !this$lastModifier.equals(other$lastModifier)) {
      return false;
    }
    final Object this$collectionId = getCollectionId();
    final Object other$collectionId = other.getCollectionId();
    if (this$collectionId == null
        ? other$collectionId != null
        : !this$collectionId.equals(other$collectionId)) {
      return false;
    }
    return true;
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
