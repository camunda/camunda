/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process;

import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;

public class SingleProcessReportDefinitionDto extends SingleReportDefinitionDto<ProcessReportDataDto> {

  public SingleProcessReportDefinitionDto() {
    super(new ProcessReportDataDto(), false, ReportType.PROCESS);
  }

  public SingleProcessReportDefinitionDto(final ProcessReportDataDto data) {
    super(data, false, ReportType.PROCESS);
  }

  @Override
  public ReportType getReportType() {
    return super.getReportType();
  }

}
