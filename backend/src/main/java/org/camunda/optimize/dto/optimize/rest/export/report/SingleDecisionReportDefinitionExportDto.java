/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.export.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;

import javax.validation.constraints.NotNull;

import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_DECISION_REPORT;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Data
public class SingleDecisionReportDefinitionExportDto extends ReportDefinitionExportDto {
  @NotNull
  private DecisionReportDataDto data;

  public SingleDecisionReportDefinitionExportDto(final SingleDecisionReportDefinitionRequestDto reportDefinition) {
    super(
      reportDefinition.getId(),
      SINGLE_DECISION_REPORT,
      SingleDecisionReportIndex.VERSION,
      reportDefinition.getName(),
      reportDefinition.getDescription(),
      reportDefinition.getCollectionId()
    );
    this.data = reportDefinition.getData();
  }

  @Override
  public ExportEntityType getExportEntityType() {
    return SINGLE_DECISION_REPORT;
  }
}
