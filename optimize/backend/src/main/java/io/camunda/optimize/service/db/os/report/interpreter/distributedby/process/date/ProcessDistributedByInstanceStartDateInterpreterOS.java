/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.date;

import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_INSTANCE_START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;

import io.camunda.optimize.service.db.os.report.filter.ProcessQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.service.DateAggregationServiceOS;
import io.camunda.optimize.service.db.os.report.service.MinMaxStatsServiceOS;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessDistributedByInstanceStartDateInterpreterOS
    extends AbstractProcessDistributedByInstanceDateInterpreterOS {

  private final ProcessViewInterpreterFacadeOS viewInterpreter;
  private final DateAggregationServiceOS dateAggregationService;
  private final ProcessQueryFilterEnhancerOS queryFilterEnhancer;
  private final MinMaxStatsServiceOS minMaxStatsService;

  public ProcessDistributedByInstanceStartDateInterpreterOS(
      final ProcessViewInterpreterFacadeOS viewInterpreter,
      final DateAggregationServiceOS dateAggregationService,
      final ProcessQueryFilterEnhancerOS queryFilterEnhancer,
      final MinMaxStatsServiceOS minMaxStatsService) {
    this.viewInterpreter = viewInterpreter;
    this.dateAggregationService = dateAggregationService;
    this.queryFilterEnhancer = queryFilterEnhancer;
    this.minMaxStatsService = minMaxStatsService;
  }

  @Override
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return Set.of(PROCESS_DISTRIBUTED_BY_INSTANCE_START_DATE);
  }

  @Override
  public String getDateField() {
    return START_DATE;
  }

  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return this.viewInterpreter;
  }

  public DateAggregationServiceOS getDateAggregationService() {
    return this.dateAggregationService;
  }

  public ProcessQueryFilterEnhancerOS getQueryFilterEnhancer() {
    return this.queryFilterEnhancer;
  }

  public MinMaxStatsServiceOS getMinMaxStatsService() {
    return this.minMaxStatsService;
  }
}
