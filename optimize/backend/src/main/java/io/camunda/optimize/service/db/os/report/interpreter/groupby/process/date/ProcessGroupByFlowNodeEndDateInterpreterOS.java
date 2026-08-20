/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.date;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_FLOW_NODE_END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.service.DateAggregationServiceOS;
import io.camunda.optimize.service.db.os.report.service.MinMaxStatsServiceOS;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByFlowNodeEndDateInterpreterOS
    extends AbstractProcessGroupByFlowNodeDateInterpreterOS {
  private final DateAggregationServiceOS dateAggregationService;
  private final MinMaxStatsServiceOS minMaxStatsService;
  private final DefinitionService definitionService;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public ProcessGroupByFlowNodeEndDateInterpreterOS(
      final DateAggregationServiceOS dateAggregationService,
      final MinMaxStatsServiceOS minMaxStatsService,
      final DefinitionService definitionService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    super();
    this.dateAggregationService = dateAggregationService;
    this.minMaxStatsService = minMaxStatsService;
    this.definitionService = definitionService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public DateAggregationServiceOS getDateAggregationService() {
    return dateAggregationService;
  }

  @Override
  public MinMaxStatsServiceOS getMinMaxStatsService() {
    return minMaxStatsService;
  }

  @Override
  protected String getDateField() {
    return FLOW_NODE_INSTANCES + "." + FlowNodeInstanceDto.Fields.endDate;
  }

  @Override
  public DefinitionService getDefinitionService() {
    return definitionService;
  }

  @Override
  public ProcessDistributedByInterpreterFacadeOS getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_FLOW_NODE_END_DATE);
  }
}
