/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.process;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_INSTANCE_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.process.AgentRunExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AgentRunExecutionPlanInterpreterES extends AbstractProcessExecutionPlanInterpreterES
    implements AgentRunExecutionPlanInterpreter {

  private final ProcessDefinitionReader processDefinitionReader;
  private final OptimizeElasticsearchClient esClient;
  private final ProcessQueryFilterEnhancerES queryFilterEnhancer;
  private final ProcessGroupByInterpreterFacadeES groupByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;

  public AgentRunExecutionPlanInterpreterES(
      final ProcessDefinitionReader processDefinitionReader,
      final OptimizeElasticsearchClient esClient,
      final ProcessQueryFilterEnhancerES queryFilterEnhancer,
      final ProcessGroupByInterpreterFacadeES groupByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter) {
    this.processDefinitionReader = processDefinitionReader;
    this.esClient = esClient;
    this.queryFilterEnhancer = queryFilterEnhancer;
    this.groupByInterpreter = groupByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public CommandEvaluationResult<Object> interpret(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    AgentRunExecutionPlanInterpreter.validateFilters(context.getReportData());
    return super.interpret(context);
  }

  @Override
  protected String[] getIndexNames(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return getMultiIndexAlias();
  }

  @Override
  protected String[] getMultiIndexAlias() {
    return new String[] {AGENT_INSTANCE_INDEX_NAME};
  }

  @Override
  protected ProcessDefinitionReader getProcessDefinitionReader() {
    return processDefinitionReader;
  }

  @Override
  protected ProcessQueryFilterEnhancerES getQueryFilterEnhancer() {
    return queryFilterEnhancer;
  }

  @Override
  protected OptimizeElasticsearchClient getEsClient() {
    return esClient;
  }

  @Override
  public ProcessGroupByInterpreterFacadeES getGroupByInterpreter() {
    return groupByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return viewInterpreter;
  }
}
