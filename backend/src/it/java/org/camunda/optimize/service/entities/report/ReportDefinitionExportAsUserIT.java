/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities.report;

import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;

import javax.ws.rs.core.Response;
import java.util.List;

public class ReportDefinitionExportAsUserIT extends AbstractReportDefinitionExportIT {

  @Override
  protected List<ReportDefinitionExportDto> exportReportDefinitionAndReturnAsList(final String reportId) {
    return exportClient.exportReportAsJsonAndReturnExportDtosAsDemo(reportId, "my_file.json");
  }

  @Override
  protected Response exportReportDefinitionAndReturnResponse(final String reportId) {
    return exportClient.exportReportAsJsonAsDemo(reportId, "my_file.json");
  }

}
