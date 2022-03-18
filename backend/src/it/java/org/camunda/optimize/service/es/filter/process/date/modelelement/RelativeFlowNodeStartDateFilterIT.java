/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process.date.modelelement;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;

import java.time.OffsetDateTime;
import java.util.List;

public class RelativeFlowNodeStartDateFilterIT extends AbstractRelativeFlowNodeDateFilterIT {

  @Override
  protected ProcessGroupByType getDateReportGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

  @Override
  protected void updateFlowNodeDate(final String instanceId, final String flowNodeId, final OffsetDateTime newDate) {
    engineDatabaseExtension.changeFlowNodeStartDate(instanceId, flowNodeId, newDate);
  }

  @Override
  protected List<ProcessFilterDto<?>> createRelativeDateViewFilter(final Long value, final DateUnit unit) {
    return ProcessFilterBuilder.filter()
      .relativeFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(value, unit)
      .add()
      .buildList();
  }

  @Override
  protected List<ProcessFilterDto<?>> createRelativeDateInstanceFilter(final List<String> flowNodeIds,
                                                                       final Long value, final DateUnit unit) {
    return ProcessFilterBuilder.filter()
      .relativeFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .flowNodeIds(flowNodeIds)
      .start(value, unit)
      .add()
      .buildList();
  }

}
