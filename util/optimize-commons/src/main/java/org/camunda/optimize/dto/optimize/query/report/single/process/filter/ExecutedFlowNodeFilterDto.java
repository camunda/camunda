/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;

import java.util.Arrays;
import java.util.List;

public class ExecutedFlowNodeFilterDto extends ProcessFilterDto<ExecutedFlowNodeFilterDataDto> {
  @Override
  public List<FilterApplicationLevel> validApplicationLevels() {
    return Arrays.asList(FilterApplicationLevel.VIEW, FilterApplicationLevel.INSTANCE);
  }
}
