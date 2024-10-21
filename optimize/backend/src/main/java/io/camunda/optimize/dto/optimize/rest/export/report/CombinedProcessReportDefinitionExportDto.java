/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.export.report;

import static io.camunda.optimize.dto.optimize.rest.export.ExportEntityType.COMBINED_REPORT;

import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex;
import jakarta.validation.constraints.NotNull;

public class CombinedProcessReportDefinitionExportDto extends ReportDefinitionExportDto {

  @NotNull private CombinedReportDataDto data;

  public CombinedProcessReportDefinitionExportDto(
      final CombinedReportDefinitionRequestDto reportDefinition) {
    super(
        reportDefinition.getId(),
        COMBINED_REPORT,
        CombinedReportIndex.VERSION,
        reportDefinition.getName(),
        reportDefinition.getDescription(),
        reportDefinition.getCollectionId());
    data = reportDefinition.getData();
  }

  public CombinedProcessReportDefinitionExportDto(@NotNull final CombinedReportDataDto data) {
    this.data = data;
  }

  public CombinedProcessReportDefinitionExportDto() {}

  @Override
  public ExportEntityType getExportEntityType() {
    return COMBINED_REPORT;
  }

  public @NotNull CombinedReportDataDto getData() {
    return data;
  }

  public void setData(@NotNull final CombinedReportDataDto data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return "CombinedProcessReportDefinitionExportDto(data=" + getData() + ")";
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof CombinedProcessReportDefinitionExportDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }
}
