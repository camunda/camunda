/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.alert;

import java.time.OffsetDateTime;

public class AlertDefinitionDto extends AlertCreationRequestDto {

  protected String id;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected boolean triggered;

  public AlertDefinitionDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
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

  public boolean isTriggered() {
    return triggered;
  }

  public void setTriggered(final boolean triggered) {
    this.triggered = triggered;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof AlertDefinitionDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $lastModified = getLastModified();
    result = result * PRIME + ($lastModified == null ? 43 : $lastModified.hashCode());
    final Object $created = getCreated();
    result = result * PRIME + ($created == null ? 43 : $created.hashCode());
    final Object $owner = getOwner();
    result = result * PRIME + ($owner == null ? 43 : $owner.hashCode());
    final Object $lastModifier = getLastModifier();
    result = result * PRIME + ($lastModifier == null ? 43 : $lastModifier.hashCode());
    result = result * PRIME + (isTriggered() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AlertDefinitionDto)) {
      return false;
    }
    final AlertDefinitionDto other = (AlertDefinitionDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
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
    if (isTriggered() != other.isTriggered()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AlertDefinitionDto(id="
        + getId()
        + ", lastModified="
        + getLastModified()
        + ", created="
        + getCreated()
        + ", owner="
        + getOwner()
        + ", lastModifier="
        + getLastModifier()
        + ", triggered="
        + isTriggered()
        + ")";
  }

  /** Needed to inherit field name constants from {@link AlertCreationRequestDto} */
  public static class Fields extends AlertCreationRequestDto.Fields {

    public static final String id = "id";
    public static final String lastModified = "lastModified";
    public static final String created = "created";
    public static final String owner = "owner";
    public static final String lastModifier = "lastModifier";
    public static final String triggered = "triggered";
  }
}
