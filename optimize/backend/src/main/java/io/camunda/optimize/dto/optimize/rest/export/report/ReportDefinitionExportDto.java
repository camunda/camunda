/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.export.report;

import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class ReportDefinitionExportDto extends OptimizeEntityExportDto {

  private String collectionId;

  protected ReportDefinitionExportDto(
      final String id,
      final ExportEntityType exportEntityType,
      final int sourceIndexVersion,
      final String name,
      final String description,
      final String collectionId) {
    super(id, exportEntityType, name, description, sourceIndexVersion);
    this.collectionId = collectionId;
  }

  public static ReportDefinitionExportDto mapReportDefinitionToExportDto(
      final ReportDefinitionDto<?> reportDef) {
    if (ReportType.PROCESS.equals(reportDef.getReportType())) {
      if (reportDef.isCombined()) {
        return new CombinedProcessReportDefinitionExportDto(
            (CombinedReportDefinitionRequestDto) reportDef);
      }
      return new SingleProcessReportDefinitionExportDto(
          (SingleProcessReportDefinitionRequestDto) reportDef);
    } else {
      return new SingleDecisionReportDefinitionExportDto(
          (SingleDecisionReportDefinitionRequestDto) reportDef);
    }
  }

  public static final class Fields {

    public static final String collectionId = "collectionId";
  }
}
