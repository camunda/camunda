/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityData;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.service.util.IdGenerator;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class DashboardDefinitionRestDto extends BaseDashboardDefinitionDto implements CollectionEntity {

  @Valid
  protected List<DashboardReportTileDto> tiles = new ArrayList<>();

  @JsonIgnore
  public Set<String> getTileIds() {
    return tiles.stream().map(DashboardReportTileDto::getId).filter(IdGenerator::isValidId).collect(toSet());
  }

  @Override
  public EntityResponseDto toEntityDto(final RoleType roleType) {
    return new EntityResponseDto(
      getId(), getName(), getDescription(), getLastModified(), getCreated(), getOwner(), getLastModifier(), EntityType.DASHBOARD,
      new EntityData(Map.of(EntityType.REPORT, (long) getTiles().size())),
      roleType
    );
  }
}
