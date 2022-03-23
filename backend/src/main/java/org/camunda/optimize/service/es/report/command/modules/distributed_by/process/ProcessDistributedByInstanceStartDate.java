/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.StartDateDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.DateDistributedByValueDto;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.service.DateAggregationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDistributedByInstanceStartDate extends AbstractProcessDistributedByInstanceDate {

  public ProcessDistributedByInstanceStartDate(final DateAggregationService dateAggregationService,
                                               final MinMaxStatsService minMaxStatsService,
                                               final ProcessQueryFilterEnhancer queryFilterEnhancer) {
    super(dateAggregationService, minMaxStatsService, queryFilterEnhancer);
  }

  @Override
  protected ProcessReportDistributedByDto<DateDistributedByValueDto> getDistributedBy() {
    return new StartDateDistributedByDto();
  }

  @Override
  public String getDateField() {
    return START_DATE;
  }
}
