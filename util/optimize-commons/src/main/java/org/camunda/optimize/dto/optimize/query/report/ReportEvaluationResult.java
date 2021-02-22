/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.ZoneId;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public abstract class ReportEvaluationResult {

  @NonNull
  protected ReportDefinitionDto<?> reportDefinition;

  public String getId() {
    return reportDefinition.getId();
  }

  public abstract List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final ZoneId timezone);

}
