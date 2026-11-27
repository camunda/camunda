/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.date;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_INSTANCE_INDEX_NAME;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_AGENT_RUN_END_DATE;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.filter.AgentRunQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.DateAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Groups agent runs by their end date. Buckets, their bounds and the date filters that limit them
 * are all read from the agent run index and its own fields.
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByAgentRunEndDateInterpreterES
    extends AbstractProcessGroupByProcessInstanceDateInterpreterES {

  private final ConfigurationService configurationService;
  private final DateAggregationServiceES dateAggregationService;
  private final MinMaxStatsServiceES minMaxStatsService;
  private final AgentRunQueryFilterEnhancerES queryFilterEnhancer;
  private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;

  public ProcessGroupByAgentRunEndDateInterpreterES(
      final ConfigurationService configurationService,
      final DateAggregationServiceES dateAggregationService,
      final MinMaxStatsServiceES minMaxStatsService,
      final AgentRunQueryFilterEnhancerES queryFilterEnhancer,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter) {
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

  protected DateAggregationServiceES getDateAggregationService() {
    return this.dateAggregationService;
  }

  protected MinMaxStatsServiceES getMinMaxStatsService() {
    return this.minMaxStatsService;
  }

  protected AgentRunQueryFilterEnhancerES getQueryFilterEnhancer() {
    return this.queryFilterEnhancer;
  }

  public ProcessDistributedByInterpreterFacadeES getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }
}
