/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities.dashboard;

import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;

import javax.ws.rs.core.Response;
import java.util.List;

public class DashboardDefinitionExportAsUserIT extends AbstractDashboardDefinitionExportIT {

  @Override
  protected List<OptimizeEntityExportDto> exportDashboardDefinitionAndReturnAsList(final String dashboardId) {
    return exportClient.exportDashboardAndReturnExportDtosAsDemo(dashboardId, "my_file.json");
  }

  @Override
  protected Response exportDashboardDefinitionAndReturnResponse(final String dashboardId) {
    return exportClient.exportDashboardDefinitionAsDemo(dashboardId, "my_file.json");
  }
}
