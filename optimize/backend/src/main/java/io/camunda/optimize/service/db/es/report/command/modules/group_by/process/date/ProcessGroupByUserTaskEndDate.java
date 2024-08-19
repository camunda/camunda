/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.group_by.process.date;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.EndDateGroupByDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.MinMaxStatsService;
import io.camunda.optimize.service.db.es.report.command.service.DateAggregationService;
import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByUserTaskEndDate extends ProcessGroupByUserTaskDate {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ProcessGroupByUserTaskEndDate.class);

  public ProcessGroupByUserTaskEndDate(
      final DateAggregationService dateAggregationService,
      final MinMaxStatsService minMaxStatsService,
      final DefinitionService definitionService) {
    super(dateAggregationService, minMaxStatsService, definitionService);
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new EndDateGroupByDto());
  }

  @Override
  protected String getDateField() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_END_DATE;
  }
}
