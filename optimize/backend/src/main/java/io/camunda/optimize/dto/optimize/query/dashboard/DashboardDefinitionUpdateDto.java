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
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DashboardDefinitionUpdateDto that = (DashboardDefinitionUpdateDto) o;
    return Objects.equals(name, that.name)
        && Objects.equals(description, that.description)
        && Objects.equals(lastModified, that.lastModified)
        && Objects.equals(lastModifier, that.lastModifier)
        && Objects.equals(tiles, that.tiles)
        && Objects.equals(collectionId, that.collectionId)
        && Objects.equals(availableFilters, that.availableFilters)
        && Objects.equals(refreshRateSeconds, that.refreshRateSeconds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name,
        description,
        lastModified,
        lastModifier,
        tiles,
        collectionId,
        availableFilters,
        refreshRateSeconds);
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
