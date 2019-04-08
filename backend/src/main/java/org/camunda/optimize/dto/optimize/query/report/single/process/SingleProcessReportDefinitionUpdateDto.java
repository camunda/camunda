/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SingleProcessReportDefinitionUpdateDto extends ReportDefinitionUpdateDto {

  protected ProcessReportDataDto data;

  public ProcessReportDataDto getData() {
    return data;
  }

  public void setData(ProcessReportDataDto data) {
    this.data = data;
  }

}
