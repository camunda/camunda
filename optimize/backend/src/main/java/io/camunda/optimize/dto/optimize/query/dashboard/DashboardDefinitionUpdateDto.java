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
    final int PRIME = 59;
    int result = 1;
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $description = getDescription();
    result = result * PRIME + ($description == null ? 43 : $description.hashCode());
    final Object $lastModified = getLastModified();
    result = result * PRIME + ($lastModified == null ? 43 : $lastModified.hashCode());
    final Object $lastModifier = getLastModifier();
    result = result * PRIME + ($lastModifier == null ? 43 : $lastModifier.hashCode());
    final Object $tiles = getTiles();
    result = result * PRIME + ($tiles == null ? 43 : $tiles.hashCode());
    final Object $collectionId = getCollectionId();
    result = result * PRIME + ($collectionId == null ? 43 : $collectionId.hashCode());
    final Object $availableFilters = getAvailableFilters();
    result = result * PRIME + ($availableFilters == null ? 43 : $availableFilters.hashCode());
    final Object $refreshRateSeconds = getRefreshRateSeconds();
    result = result * PRIME + ($refreshRateSeconds == null ? 43 : $refreshRateSeconds.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DashboardDefinitionUpdateDto)) {
      return false;
    }
    final DashboardDefinitionUpdateDto other = (DashboardDefinitionUpdateDto) o;
    if (!other.canEqual((Object) this)) {
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
    final Object this$lastModifier = getLastModifier();
    final Object other$lastModifier = other.getLastModifier();
    if (this$lastModifier == null
        ? other$lastModifier != null
        : !this$lastModifier.equals(other$lastModifier)) {
      return false;
    }
    final Object this$tiles = getTiles();
    final Object other$tiles = other.getTiles();
    if (this$tiles == null ? other$tiles != null : !this$tiles.equals(other$tiles)) {
      return false;
    }
    final Object this$collectionId = getCollectionId();
    final Object other$collectionId = other.getCollectionId();
    if (this$collectionId == null
        ? other$collectionId != null
        : !this$collectionId.equals(other$collectionId)) {
      return false;
    }
    final Object this$availableFilters = getAvailableFilters();
    final Object other$availableFilters = other.getAvailableFilters();
    if (this$availableFilters == null
        ? other$availableFilters != null
        : !this$availableFilters.equals(other$availableFilters)) {
      return false;
    }
    final Object this$refreshRateSeconds = getRefreshRateSeconds();
    final Object other$refreshRateSeconds = other.getRefreshRateSeconds();
    if (this$refreshRateSeconds == null
        ? other$refreshRateSeconds != null
        : !this$refreshRateSeconds.equals(other$refreshRateSeconds)) {
      return false;
    }
    return true;
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
