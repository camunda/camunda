/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process.date.modelelement;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

import java.util.List;

import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;

public class RollingFlowNodeStartDateFilterIT extends AbstractFlowNodeStartDateFilterIT {
  @Override
  protected List<ProcessFilterDto<?>> createDateFilterForDate1() {
    dateFreezer().dateToFreeze(DATE_1).freezeDateAndReturn();
    return createRollingDateFilter(0L, DateFilterUnit.DAYS);
  }

  @Override
  protected List<ProcessFilterDto<?>> createDateFilterForDate2() {
    dateFreezer().dateToFreeze(DATE_2).freezeDateAndReturn();
    return createRollingDateFilter(0L, DateFilterUnit.DAYS);
  }

  @Override
  protected List<ProcessFilterDto<?>> createInvalidFilter() {
    return createRollingDateFilter(null, null);
  }
}
