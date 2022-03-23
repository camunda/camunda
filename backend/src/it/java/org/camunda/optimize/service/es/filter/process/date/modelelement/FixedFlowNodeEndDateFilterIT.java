/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process.date.modelelement;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;

import java.time.OffsetDateTime;
import java.util.List;

public class FixedFlowNodeEndDateFilterIT extends AbstractFixedFlowNodeDateFilterIT {
  @Override
  protected void updateFlowNodeDate(final String instanceId, final String flowNodeId, final OffsetDateTime newDate) {
    engineDatabaseExtension.changeFlowNodeEndDate(instanceId, flowNodeId, newDate);
  }

  @Override
  protected ProcessGroupByType getDateReportGroupByType() {
    return ProcessGroupByType.END_DATE;
  }


  @Override
  protected List<ProcessFilterDto<?>> createFixedDateViewFilter(final OffsetDateTime startDate,
                                                                final OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter()
      .fixedFlowNodeEndDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(startDate)
      .end(endDate)
      .add()
      .buildList();
  }

  @Override
  protected List<ProcessFilterDto<?>> createFixedDateInstanceFilter(final List<String> flowNodeIds,
                                                                    final OffsetDateTime startDate,
                                                                    final OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter()
      .fixedFlowNodeEndDate()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .flowNodeIds(flowNodeIds)
      .start(startDate)
      .end(endDate)
      .add()
      .buildList();
  }
}
