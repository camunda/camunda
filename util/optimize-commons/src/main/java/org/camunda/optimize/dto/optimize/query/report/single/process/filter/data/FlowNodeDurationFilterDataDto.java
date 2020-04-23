/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.data;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowNodeDurationFilterDataDto extends DurationFilterDataDto {
  private String flowNodeId;

  @Builder
  public FlowNodeDurationFilterDataDto(final Long value,
                                       final DurationFilterUnit unit,
                                       final String operator,
                                       final String flowNodeId) {
    super(value, unit, operator);
    this.flowNodeId = flowNodeId;
  }
}
