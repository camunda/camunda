/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.export.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;

import javax.validation.constraints.NotNull;

import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_PROCESS_REPORT;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Data
public class SingleProcessReportDefinitionExportDto extends ReportDefinitionExportDto {
  @NotNull
  private ProcessReportDataDto data;

  public SingleProcessReportDefinitionExportDto(final SingleProcessReportDefinitionRequestDto reportDefinition) {
    super(
      SingleProcessReportIndex.VERSION,
      reportDefinition.getId(),
      reportDefinition.getName(),
      reportDefinition.getCollectionId()
    );
    this.data = reportDefinition.getData();
  }

  @Override
  public ExportEntityType getExportEntityType() {
    return SINGLE_PROCESS_REPORT;
  }
}

