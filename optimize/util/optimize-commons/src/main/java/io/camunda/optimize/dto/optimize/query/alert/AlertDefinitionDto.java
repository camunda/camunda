/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.alert;

import java.time.OffsetDateTime;
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final AlertDefinitionDto that = (AlertDefinitionDto) o;
    return triggered == that.triggered
        && Objects.equals(id, that.id)
        && Objects.equals(lastModified, that.lastModified)
        && Objects.equals(created, that.created)
        && Objects.equals(owner, that.owner)
        && Objects.equals(lastModifier, that.lastModifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), id, lastModified, created, owner, lastModifier, triggered);
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
  @SuppressWarnings("checkstyle:ConstantName")
  public static class Fields extends AlertCreationRequestDto.Fields {

    public static final String id = "id";
    public static final String lastModified = "lastModified";
    public static final String created = "created";
    public static final String owner = "owner";
    public static final String lastModifier = "lastModifier";
    public static final String triggered = "triggered";
  }
}
