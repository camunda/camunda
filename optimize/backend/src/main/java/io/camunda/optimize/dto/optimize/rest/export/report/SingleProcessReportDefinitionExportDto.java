/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.export.report;

import static io.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_PROCESS_REPORT;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Data
public class SingleProcessReportDefinitionExportDto extends ReportDefinitionExportDto {
  @NotNull private ProcessReportDataDto data;

  public SingleProcessReportDefinitionExportDto(
      final SingleProcessReportDefinitionRequestDto reportDefinition) {
    super(
        reportDefinition.getId(),
        SINGLE_PROCESS_REPORT,
        SingleProcessReportIndex.VERSION,
        reportDefinition.getName(),
        reportDefinition.getDescription(),
        reportDefinition.getCollectionId());
    this.data = reportDefinition.getData();
  }

  @Override
  public ExportEntityType getExportEntityType() {
    return SINGLE_PROCESS_REPORT;
  }
}
