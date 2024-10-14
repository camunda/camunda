/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.entity;

import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.RoleType;
import java.time.OffsetDateTime;

public class EntityResponseDto {

  private String id;
  private String name;
  private String description;
  private OffsetDateTime lastModified;
  private OffsetDateTime created;
  private String owner;
  private String lastModifier;
  private EntityType entityType;
  private EntityData data;
  private Boolean combined;
  private ReportType reportType;
  private RoleType currentUserRole;

  public EntityResponseDto(
      final String id,
      final String name,
      final String description,
      final OffsetDateTime lastModified,
      final OffsetDateTime created,
      final String owner,
      final String lastModifier,
      final EntityType entityType,
      final EntityData data,
      final RoleType currentUserRole) {
    this(
        id,
        name,
        description,
        lastModified,
        created,
        owner,
        lastModifier,
        entityType,
        data,
        null,
        null,
        currentUserRole);
  }

  public EntityResponseDto(
      final String id,
      final String name,
      final String description,
      final OffsetDateTime lastModified,
      final OffsetDateTime created,
      final String owner,
      final String lastModifier,
      final EntityType entityType,
      final Boolean combined,
      final ReportType reportType,
      final RoleType currentUserRole) {
    this(
        id,
        name,
        description,
        lastModified,
        created,
        owner,
        lastModifier,
        entityType,
        new EntityData(),
        combined,
        reportType,
        currentUserRole);
  }

  public EntityResponseDto(
      final String id,
      final String name,
      final String description,
      final OffsetDateTime lastModified,
      final OffsetDateTime created,
      final String owner,
      final String lastModifier,
      final EntityType entityType,
      final EntityData data,
      final Boolean combined,
      final ReportType reportType,
      final RoleType currentUserRole) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.lastModified = lastModified;
    this.created = created;
    this.owner = owner;
    this.lastModifier = lastModifier;
    this.entityType = entityType;
    this.data = data;
    this.combined = combined;
    this.reportType = reportType;
    this.currentUserRole = currentUserRole;
  }

  protected EntityResponseDto() {}

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

  public EntityType getEntityType() {
    return entityType;
  }

  public void setEntityType(final EntityType entityType) {
    this.entityType = entityType;
  }

  public EntityData getData() {
    return data;
  }

  public void setData(final EntityData data) {
    this.data = data;
  }

  public Boolean getCombined() {
    return combined;
  }

  public void setCombined(final Boolean combined) {
    this.combined = combined;
  }

  public ReportType getReportType() {
    return reportType;
  }

  public void setReportType(final ReportType reportType) {
    this.reportType = reportType;
  }

  public RoleType getCurrentUserRole() {
    return currentUserRole;
  }

  public void setCurrentUserRole(final RoleType currentUserRole) {
    this.currentUserRole = currentUserRole;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EntityResponseDto;
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
    final Object $entityType = getEntityType();
    result = result * PRIME + ($entityType == null ? 43 : $entityType.hashCode());
    final Object $data = getData();
    result = result * PRIME + ($data == null ? 43 : $data.hashCode());
    final Object $combined = getCombined();
    result = result * PRIME + ($combined == null ? 43 : $combined.hashCode());
    final Object $reportType = getReportType();
    result = result * PRIME + ($reportType == null ? 43 : $reportType.hashCode());
    final Object $currentUserRole = getCurrentUserRole();
    result = result * PRIME + ($currentUserRole == null ? 43 : $currentUserRole.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EntityResponseDto)) {
      return false;
    }
    final EntityResponseDto other = (EntityResponseDto) o;
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
    final Object this$entityType = getEntityType();
    final Object other$entityType = other.getEntityType();
    if (this$entityType == null
        ? other$entityType != null
        : !this$entityType.equals(other$entityType)) {
      return false;
    }
    final Object this$data = getData();
    final Object other$data = other.getData();
    if (this$data == null ? other$data != null : !this$data.equals(other$data)) {
      return false;
    }
    final Object this$combined = getCombined();
    final Object other$combined = other.getCombined();
    if (this$combined == null ? other$combined != null : !this$combined.equals(other$combined)) {
      return false;
    }
    final Object this$reportType = getReportType();
    final Object other$reportType = other.getReportType();
    if (this$reportType == null
        ? other$reportType != null
        : !this$reportType.equals(other$reportType)) {
      return false;
    }
    final Object this$currentUserRole = getCurrentUserRole();
    final Object other$currentUserRole = other.getCurrentUserRole();
    if (this$currentUserRole == null
        ? other$currentUserRole != null
        : !this$currentUserRole.equals(other$currentUserRole)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EntityResponseDto(id="
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
        + ", entityType="
        + getEntityType()
        + ", data="
        + getData()
        + ", combined="
        + getCombined()
        + ", reportType="
        + getReportType()
        + ", currentUserRole="
        + getCurrentUserRole()
        + ")";
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String name = "name";
    public static final String description = "description";
    public static final String lastModified = "lastModified";
    public static final String created = "created";
    public static final String owner = "owner";
    public static final String lastModifier = "lastModifier";
    public static final String entityType = "entityType";
    public static final String data = "data";
    public static final String combined = "combined";
    public static final String reportType = "reportType";
    public static final String currentUserRole = "currentUserRole";
  }
}
