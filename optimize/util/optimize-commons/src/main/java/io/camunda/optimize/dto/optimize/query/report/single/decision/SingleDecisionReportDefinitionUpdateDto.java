/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.decision;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SingleDecisionReportDefinitionUpdateDto extends ReportDefinitionUpdateDto {

  protected DecisionReportDataDto data;

  public DecisionReportDataDto getData() {
    return data;
  }

  public void setData(final DecisionReportDataDto data) {
    this.data = data;
  }
}
