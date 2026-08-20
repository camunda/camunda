/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.date;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_FLOW_NODE_END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.DateAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByFlowNodeEndDateInterpreterES
    extends AbstractProcessGroupByFlowNodeDateInterpreterES {

  private final DateAggregationServiceES dateAggregationService;
  private final MinMaxStatsServiceES minMaxStatsService;
  private final DefinitionService definitionService;
  private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;

  public ProcessGroupByFlowNodeEndDateInterpreterES(
      final DateAggregationServiceES dateAggregationService,
      final MinMaxStatsServiceES minMaxStatsService,
      final DefinitionService definitionService,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter) {
    this.dateAggregationService = dateAggregationService;
    this.minMaxStatsService = minMaxStatsService;
    this.definitionService = definitionService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_FLOW_NODE_END_DATE);
  }

  @Override
  protected String getDateField() {
    return FLOW_NODE_INSTANCES + "." + FlowNodeInstanceDto.Fields.endDate;
  }

  public DateAggregationServiceES getDateAggregationService() {
    return this.dateAggregationService;
  }

  public MinMaxStatsServiceES getMinMaxStatsService() {
    return this.minMaxStatsService;
  }

  public DefinitionService getDefinitionService() {
    return this.definitionService;
  }

  public ProcessDistributedByInterpreterFacadeES getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }
}
