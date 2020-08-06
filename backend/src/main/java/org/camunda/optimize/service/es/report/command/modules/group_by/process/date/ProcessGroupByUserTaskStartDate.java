/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.date;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.util.DateAggregationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_START_DATE;

@Slf4j
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByUserTaskStartDate extends ProcessGroupByUserTaskDate {

  public ProcessGroupByUserTaskStartDate(final DateAggregationService dateAggregationService,
                                         final MinMaxStatsService minMaxStatsService) {
    super(dateAggregationService, minMaxStatsService);
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new StartDateGroupByDto());
  }

  @Override
  protected String getDateField() {
    return USER_TASKS + "." + USER_TASK_START_DATE;
  }

}
