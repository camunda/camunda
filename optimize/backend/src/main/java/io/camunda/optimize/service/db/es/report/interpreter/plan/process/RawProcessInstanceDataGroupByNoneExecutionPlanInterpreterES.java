/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_RAW_PROCESS_INSTANCE_DATA_GROUP_BY_NONE;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.process.RawProcessInstanceDataGroupByNoneExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class RawProcessInstanceDataGroupByNoneExecutionPlanInterpreterES
    extends AbstractProcessExecutionPlanInterpreterES
    implements RawProcessInstanceDataGroupByNoneExecutionPlanInterpreter {

  private final ProcessGroupByInterpreterFacadeES groupByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final OptimizeElasticsearchClient esClient;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessQueryFilterEnhancerES queryFilterEnhancer;

  public RawProcessInstanceDataGroupByNoneExecutionPlanInterpreterES(
      final ProcessGroupByInterpreterFacadeES groupByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter,
      final OptimizeElasticsearchClient esClient,
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessQueryFilterEnhancerES queryFilterEnhancer) {
    this.groupByInterpreter = groupByInterpreter;
    this.viewInterpreter = viewInterpreter;
    this.esClient = esClient;
    this.processDefinitionReader = processDefinitionReader;
    this.queryFilterEnhancer = queryFilterEnhancer;
  }

  @Override
  public Set<ProcessExecutionPlan> getSupportedExecutionPlans() {
    return Set.of(PROCESS_RAW_PROCESS_INSTANCE_DATA_GROUP_BY_NONE);
  }

  @Override
  public CommandEvaluationResult<Object> interpret(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> executionContext) {
    final CommandEvaluationResult<Object> commandResult = super.interpret(executionContext);
    addNewVariablesAndDtoFieldsToTableColumnConfig(executionContext, commandResult);
    return commandResult;
  }

  public ProcessGroupByInterpreterFacadeES getGroupByInterpreter() {
    return this.groupByInterpreter;
  }

  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }

  public OptimizeElasticsearchClient getEsClient() {
    return this.esClient;
  }

  public ProcessDefinitionReader getProcessDefinitionReader() {
    return this.processDefinitionReader;
  }

  public ProcessQueryFilterEnhancerES getQueryFilterEnhancer() {
    return this.queryFilterEnhancer;
  }
}
