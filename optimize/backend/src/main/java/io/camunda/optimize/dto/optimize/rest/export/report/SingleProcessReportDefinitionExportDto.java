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
    data = reportDefinition.getData();
  }

  public SingleProcessReportDefinitionExportDto(@NotNull final ProcessReportDataDto data) {
    this.data = data;
  }

  public SingleProcessReportDefinitionExportDto() {}

  @Override
  public ExportEntityType getExportEntityType() {
    return SINGLE_PROCESS_REPORT;
  }

  public @NotNull ProcessReportDataDto getData() {
    return data;
  }

  public void setData(@NotNull final ProcessReportDataDto data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return "SingleProcessReportDefinitionExportDto(data=" + getData() + ")";
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof SingleProcessReportDefinitionExportDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }
}
