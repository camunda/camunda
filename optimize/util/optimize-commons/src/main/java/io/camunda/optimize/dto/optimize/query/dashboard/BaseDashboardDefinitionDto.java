/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard;

import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class BaseDashboardDefinitionDto {

  protected String id;
  protected String name;
  protected String description;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected String collectionId;
  protected boolean managementDashboard = false;
  protected boolean instantPreviewDashboard = false;
  protected List<DashboardFilterDto<?>> availableFilters = new ArrayList<>();
  protected Long refreshRateSeconds;

  public BaseDashboardDefinitionDto() {}

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

  public boolean isManagementDashboard() {
    return managementDashboard;
  }

  public void setManagementDashboard(final boolean managementDashboard) {
    this.managementDashboard = managementDashboard;
  }

  public boolean isInstantPreviewDashboard() {
    return instantPreviewDashboard;
  }

  public void setInstantPreviewDashboard(final boolean instantPreviewDashboard) {
    this.instantPreviewDashboard = instantPreviewDashboard;
  }

  public List<DashboardFilterDto<?>> getAvailableFilters() {
    return availableFilters;
  }

  public void setAvailableFilters(final List<DashboardFilterDto<?>> availableFilters) {
    this.availableFilters = availableFilters;
  }

  public Long getRefreshRateSeconds() {
    return refreshRateSeconds;
  }

  public void setRefreshRateSeconds(final Long refreshRateSeconds) {
    this.refreshRateSeconds = refreshRateSeconds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof BaseDashboardDefinitionDto;
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
    return "BaseDashboardDefinitionDto(id="
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
        + ", managementDashboard="
        + isManagementDashboard()
        + ", instantPreviewDashboard="
        + isInstantPreviewDashboard()
        + ", availableFilters="
        + getAvailableFilters()
        + ", refreshRateSeconds="
        + getRefreshRateSeconds()
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
    public static final String collectionId = "collectionId";
    public static final String managementDashboard = "managementDashboard";
    public static final String instantPreviewDashboard = "instantPreviewDashboard";
    public static final String availableFilters = "availableFilters";
    public static final String refreshRateSeconds = "refreshRateSeconds";
  }
}
