/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.distributed_by.process;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;

import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.StartDateDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.DateDistributedByValueDto;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.report.MinMaxStatsService;
import io.camunda.optimize.service.db.es.report.command.service.DateAggregationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDistributedByInstanceStartDate
    extends AbstractProcessDistributedByInstanceDate {

  public ProcessDistributedByInstanceStartDate(
      final DateAggregationService dateAggregationService,
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
