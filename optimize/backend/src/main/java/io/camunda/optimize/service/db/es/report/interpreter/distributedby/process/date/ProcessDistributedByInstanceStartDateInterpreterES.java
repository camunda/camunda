/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.date;

import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_INSTANCE_START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;

import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.DateAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessDistributedByInstanceStartDateInterpreterES
    extends AbstractProcessDistributedByInstanceDateInterpreterES {

  private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final DateAggregationServiceES dateAggregationService;
  private final ProcessQueryFilterEnhancerES queryFilterEnhancer;
  private final MinMaxStatsServiceES minMaxStatsService;

  public ProcessDistributedByInstanceStartDateInterpreterES(
      final ProcessViewInterpreterFacadeES viewInterpreter,
      final DateAggregationServiceES dateAggregationService,
      final ProcessQueryFilterEnhancerES queryFilterEnhancer,
      final MinMaxStatsServiceES minMaxStatsService) {
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

  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }

  public DateAggregationServiceES getDateAggregationService() {
    return this.dateAggregationService;
  }

  public ProcessQueryFilterEnhancerES getQueryFilterEnhancer() {
    return this.queryFilterEnhancer;
  }

  public MinMaxStatsServiceES getMinMaxStatsService() {
    return this.minMaxStatsService;
  }
}
