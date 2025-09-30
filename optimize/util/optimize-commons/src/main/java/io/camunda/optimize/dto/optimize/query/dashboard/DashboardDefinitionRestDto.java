/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard;

import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityData;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import io.camunda.optimize.service.util.IdGenerator;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DashboardDefinitionRestDto extends BaseDashboardDefinitionDto
    implements CollectionEntity {

  @Valid protected List<DashboardReportTileDto> tiles = new ArrayList<>();

  public DashboardDefinitionRestDto(@Valid final List<DashboardReportTileDto> tiles) {
    this.tiles = tiles;
  }

  public DashboardDefinitionRestDto() {}

  @JsonIgnore
  public Set<String> getTileIds() {
    return tiles.stream()
        .map(DashboardReportTileDto::getId)
        .filter(IdGenerator::isValidId)
        .collect(toSet());
  }

  @Override
  public EntityResponseDto toEntityDto(final RoleType roleType) {
    return new EntityResponseDto(
        getId(),
        getName(),
        getDescription(),
        getLastModified(),
        getCreated(),
        getOwner(),
        getLastModifier(),
        EntityType.DASHBOARD,
        new EntityData(Map.of(EntityType.REPORT, (long) getTiles().size())),
        roleType);
  }

  public @Valid List<DashboardReportTileDto> getTiles() {
    return tiles;
  }

  public void setTiles(@Valid final List<DashboardReportTileDto> tiles) {
    this.tiles = tiles;
  }

  @Override
  public String toString() {
    return "DashboardDefinitionRestDto(tiles=" + getTiles() + ")";
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DashboardDefinitionRestDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final DashboardDefinitionRestDto that = (DashboardDefinitionRestDto) o;
    return Objects.equals(tiles, that.tiles);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), tiles);
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String tiles = "tiles";
  }
}
