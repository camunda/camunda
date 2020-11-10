/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.export;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Data
public class SingleDecisionReportDefinitionExportDto extends ReportDefinitionExportDto {
  private DecisionReportDataDto data;

  public SingleDecisionReportDefinitionExportDto(final SingleDecisionReportDefinitionRequestDto reportDefinition) {
    super(
      SingleDecisionReportIndex.VERSION,
      ReportType.DECISION,
      reportDefinition.getId(),
      reportDefinition.getName(),
      reportDefinition.getCreated(),
      reportDefinition.getCollectionId(),
      reportDefinition.isCombined()
    );
    this.data = reportDefinition.getData();
  }
}
