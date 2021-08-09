/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process.date.modelelement;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;

import java.time.OffsetDateTime;
import java.util.List;

public abstract class AbstractFlowNodeEndDateFilterIT extends AbstractFlowNodeDateFilterIT {

  @Override
  protected void updateFlowNodeDate(final String instanceId, final String flowNodeId, final OffsetDateTime newDate) {
    engineDatabaseExtension.changeFlowNodeEndDate(instanceId, flowNodeId, newDate);
  }

  @Override
  protected ProcessGroupByType getDateReportGroupByType() {
    return ProcessGroupByType.END_DATE;
  }

  protected List<ProcessFilterDto<?>> createFixedDateFilter(final OffsetDateTime date) {
    return ProcessFilterBuilder.filter()
      .fixedFlowNodeEndDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(date)
      .end(date)
      .add()
      .buildList();
  }

  protected List<ProcessFilterDto<?>> createRelativeDateFilter(final Long value, final DateFilterUnit unit) {
    return ProcessFilterBuilder.filter()
      .relativeFlowNodeEndDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(value, unit)
      .add()
      .buildList();
  }

  protected List<ProcessFilterDto<?>> createRollingDateFilter(final Long value, final DateFilterUnit unit) {
    return ProcessFilterBuilder.filter()
      .rollingFlowNodeEndDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(value, unit)
      .add()
      .buildList();
  }

}
