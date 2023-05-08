/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.export.report;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;

@NoArgsConstructor
@FieldNameConstants
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class ReportDefinitionExportDto extends OptimizeEntityExportDto {
  private String collectionId;

  protected ReportDefinitionExportDto(final String id, final ExportEntityType exportEntityType,
                                      final int sourceIndexVersion, final String name, final String description,
                                      final String collectionId) {
    super(id, exportEntityType, name, description, sourceIndexVersion);
    this.collectionId = collectionId;
  }

  public static ReportDefinitionExportDto mapReportDefinitionToExportDto(final ReportDefinitionDto<?> reportDef) {
    if (ReportType.PROCESS.equals(reportDef.getReportType())) {
      if (reportDef.isCombined()) {
        return new CombinedProcessReportDefinitionExportDto((CombinedReportDefinitionRequestDto) reportDef);
      }
      return new SingleProcessReportDefinitionExportDto((SingleProcessReportDefinitionRequestDto) reportDef);
    } else {
      return new SingleDecisionReportDefinitionExportDto((SingleDecisionReportDefinitionRequestDto) reportDef);
    }
  }
}
