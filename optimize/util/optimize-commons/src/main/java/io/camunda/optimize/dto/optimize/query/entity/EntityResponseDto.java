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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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

  @SuppressWarnings("checkstyle:ConstantName")
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
