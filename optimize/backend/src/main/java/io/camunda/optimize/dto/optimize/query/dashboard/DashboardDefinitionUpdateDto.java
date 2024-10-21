/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardDefinitionUpdateDto {

  protected String name;
  @JsonInclude protected String description;
  protected OffsetDateTime lastModified;
  protected String lastModifier;
  protected List<DashboardReportTileDto> tiles;
  protected String collectionId;
  protected List<DashboardFilterDto<?>> availableFilters = new ArrayList<>();
  @JsonInclude protected Long refreshRateSeconds;

  public DashboardDefinitionUpdateDto() {}

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

  public String getLastModifier() {
    return lastModifier;
  }

  public void setLastModifier(final String lastModifier) {
    this.lastModifier = lastModifier;
  }

  public List<DashboardReportTileDto> getTiles() {
    return tiles;
  }

  public void setTiles(final List<DashboardReportTileDto> tiles) {
    this.tiles = tiles;
  }

  public String getCollectionId() {
    return collectionId;
  }

  public void setCollectionId(final String collectionId) {
    this.collectionId = collectionId;
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
    return other instanceof DashboardDefinitionUpdateDto;
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
    return "DashboardDefinitionUpdateDto(name="
        + getName()
        + ", description="
        + getDescription()
        + ", lastModified="
        + getLastModified()
        + ", lastModifier="
        + getLastModifier()
        + ", tiles="
        + getTiles()
        + ", collectionId="
        + getCollectionId()
        + ", availableFilters="
        + getAvailableFilters()
        + ", refreshRateSeconds="
        + getRefreshRateSeconds()
        + ")";
  }
}
