/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process.date.modelelement;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

import java.util.List;

public class FixedFlowNodeEndDateFilterIT extends AbstractFlowNodeEndDateFilterIT {
  @Override
  protected List<ProcessFilterDto<?>> createDateFilterForDate1() {
    return createFixedDateFilter(DATE_1);
  }

  @Override
  protected List<ProcessFilterDto<?>> createDateFilterForDate2() {
    return createFixedDateFilter(DATE_2);
  }

  @Override
  protected List<ProcessFilterDto<?>> createInvalidFilter() {
    return createFixedDateFilter(null);
  }
}
