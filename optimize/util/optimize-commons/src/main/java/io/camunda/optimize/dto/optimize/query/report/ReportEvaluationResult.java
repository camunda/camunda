/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

import io.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import java.time.ZoneId;
import java.util.List;
import lombok.Data;
import lombok.NonNull;

@Data
public abstract class ReportEvaluationResult {

  @NonNull protected ReportDefinitionDto<?> reportDefinition;

  public ReportEvaluationResult(@NonNull ReportDefinitionDto<?> reportDefinition) {
    this.reportDefinition = reportDefinition;
  }

  public ReportEvaluationResult() {}

  public String getId() {
    return reportDefinition.getId();
  }

  public abstract List<String[]> getResultAsCsv(
      final Integer limit, final Integer offset, final ZoneId timezone);

  public abstract PaginatedDataExportDto getResult();
}
