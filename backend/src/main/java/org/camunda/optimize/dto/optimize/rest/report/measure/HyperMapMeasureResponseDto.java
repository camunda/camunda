/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.report.measure;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class HyperMapMeasureResponseDto extends MeasureResponseDto<List<HyperMapResultEntryDto>> {
  // overridden to make sure the type is always available and correct for these classes
  @Override
  public ResultType getType() {
    return ResultType.HYPER_MAP;
  }
}
