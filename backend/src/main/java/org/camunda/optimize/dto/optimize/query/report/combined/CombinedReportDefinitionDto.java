/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.combined;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportType;

public class CombinedReportDefinitionDto extends ReportDefinitionDto<CombinedReportDataDto> {

  public CombinedReportDefinitionDto() {
    this(new CombinedReportDataDto());
  }

  public CombinedReportDefinitionDto(CombinedReportDataDto data) {
    super(data, true, ReportType.PROCESS);
  }

}
