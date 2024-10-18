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

  public ReportDefinitionExportDto() {}

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

  public String getCollectionId() {
    return collectionId;
  }

  public void setCollectionId(final String collectionId) {
    this.collectionId = collectionId;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ReportDefinitionExportDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $collectionId = getCollectionId();
    result = result * PRIME + ($collectionId == null ? 43 : $collectionId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ReportDefinitionExportDto)) {
      return false;
    }
    final ReportDefinitionExportDto other = (ReportDefinitionExportDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$collectionId = getCollectionId();
    final Object other$collectionId = other.getCollectionId();
    if (this$collectionId == null
        ? other$collectionId != null
        : !this$collectionId.equals(other$collectionId)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ReportDefinitionExportDto(collectionId=" + getCollectionId() + ")";
  }

  public static final class Fields {

    public static final String collectionId = "collectionId";
  }
}
