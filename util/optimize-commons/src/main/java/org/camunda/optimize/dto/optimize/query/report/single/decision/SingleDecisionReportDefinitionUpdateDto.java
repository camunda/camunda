/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SingleDecisionReportDefinitionUpdateDto extends ReportDefinitionUpdateDto {

  @Getter @Setter protected DecisionReportDataDto data;
}
