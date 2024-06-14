/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.query.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class DashboardDefinitionUpdateDto {
  protected String name;
  @JsonInclude protected String description;
  protected OffsetDateTime lastModified;
  protected String lastModifier;
  protected List<DashboardReportTileDto> tiles;
  protected String collectionId;
  protected List<DashboardFilterDto<?>> availableFilters = new ArrayList<>();
  @JsonInclude protected Long refreshRateSeconds;
}
