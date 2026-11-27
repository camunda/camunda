/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.date;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_INSTANCE_INDEX_NAME;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_AGENT_RUN_END_DATE;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.filter.AgentRunQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.service.DateAggregationServiceOS;
import io.camunda.optimize.service.db.os.report.service.MinMaxStatsServiceOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Groups agent runs by their end date. Buckets, their bounds and the date filters that limit them
 * are all read from the agent run index and its own fields.
 */
@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByAgentRunEndDateInterpreterOS
    extends AbstractProcessGroupByProcessInstanceDateInterpreterOS {

  private final ConfigurationService configurationService;
  private final DateAggregationServiceOS dateAggregationService;
  private final MinMaxStatsServiceOS minMaxStatsService;
  private final AgentRunQueryFilterEnhancerOS queryFilterEnhancer;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public ProcessGroupByAgentRunEndDateInterpreterOS(
      final ConfigurationService configurationService,
      final DateAggregationServiceOS dateAggregationService,
      final MinMaxStatsServiceOS minMaxStatsService,
      final AgentRunQueryFilterEnhancerOS queryFilterEnhancer,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    this.configurationService = configurationService;
    this.dateAggregationService = dateAggregationService;
    this.minMaxStatsService = minMaxStatsService;
    this.queryFilterEnhancer = queryFilterEnhancer;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_AGENT_RUN_END_DATE);
  }

  @Override
  protected String[] getIndexNames(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return new String[] {AGENT_INSTANCE_INDEX_NAME};
  }

  @Override
  public String getDateField() {
    return AgentRunIndex.END_DATE;
  }

  protected ConfigurationService getConfigurationService() {
    return this.configurationService;
  }

  protected DateAggregationServiceOS getDateAggregationService() {
    return this.dateAggregationService;
  }

  protected MinMaxStatsServiceOS getMinMaxStatsService() {
    return this.minMaxStatsService;
  }

  protected AgentRunQueryFilterEnhancerOS getQueryFilterEnhancer() {
    return this.queryFilterEnhancer;
  }

  public ProcessDistributedByInterpreterFacadeOS getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return this.viewInterpreter;
  }
}
