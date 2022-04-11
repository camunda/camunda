/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode;

import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;

import java.time.OffsetDateTime;
import java.util.List;

import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

@EqualsAndHashCode(callSuper = true)
public class FixedFlowNodeDateFilterDataDto extends FlowNodeDateFilterDataDto<OffsetDateTime> {

  @SuppressWarnings(UNUSED)
  protected FixedFlowNodeDateFilterDataDto() {
    this(null, null, null);
  }

  public FixedFlowNodeDateFilterDataDto(final List<String> flowNodeIds, final OffsetDateTime start,
                                        final OffsetDateTime end) {
    super(flowNodeIds, DateFilterType.FIXED, start, end);
  }
}
