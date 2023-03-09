/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.sharing;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;

import java.util.List;

@Data
public class DashboardShareRestDto {

  private String id;
  private String dashboardId;
  private List<DashboardReportTileDto> tileShares;
}
