/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.group_by.process.date;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.MinMaxStatsService;
import io.camunda.optimize.service.db.es.report.command.service.DateAggregationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByFlowNodeStartDate extends ProcessGroupByFlowNodeDate {

  public ProcessGroupByFlowNodeStartDate(
      final DateAggregationService dateAggregationService,
      final MinMaxStatsService minMaxStatsService,
      final DefinitionService definitionService) {
    super(dateAggregationService, minMaxStatsService, definitionService);
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setGroupBy(new StartDateGroupByDto());
  }

  @Override
  protected String getDateField() {
    return FLOW_NODE_INSTANCES + "." + FlowNodeInstanceDto.Fields.startDate;
  }
}
