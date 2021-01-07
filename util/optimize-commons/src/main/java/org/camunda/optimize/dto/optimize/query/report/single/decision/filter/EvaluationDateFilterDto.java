/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.filter;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;

@JsonTypeName("evaluationDateTime")
@NoArgsConstructor
public class EvaluationDateFilterDto extends DecisionFilterDto<DateFilterDataDto<?>> {
  public EvaluationDateFilterDto(final DateFilterDataDto<?> dateFilterDataDto) {
    super(dateFilterDataDto);
  }
}
