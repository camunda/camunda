/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode;

import static io.camunda.optimize.util.SuppressionConstants.UNUSED;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import java.util.List;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class RollingFlowNodeDateFilterDataDto
    extends FlowNodeDateFilterDataDto<RollingDateFilterStartDto> {

  @SuppressWarnings(UNUSED)
  protected RollingFlowNodeDateFilterDataDto() {
    this(null, null);
  }

  public RollingFlowNodeDateFilterDataDto(
      final List<String> flowNodeIds, final RollingDateFilterStartDto rollingDateFilterStartDto) {
    super(flowNodeIds, DateFilterType.ROLLING, rollingDateFilterStartDto, null);
  }
}
