/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

import java.time.ZoneId;
import java.util.List;

@AllArgsConstructor
@Data
public abstract class ReportEvaluationResult<R extends ReportResultDto, D extends ReportDefinitionDto> {

  @NonNull
  protected final List<R> results;
  @NonNull
  protected final D reportDefinition;

  public String getId() {
    return reportDefinition.getId();
  }

  public R getResultAsDto() {
    return results.stream().findFirst().orElse(null);
  }

  public abstract List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final ZoneId timezone);

}
