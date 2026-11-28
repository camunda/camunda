/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.plan.process;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_INSTANCE_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.report.filter.ProcessQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.process.AgentRunExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class AgentRunExecutionPlanInterpreterOS extends AbstractProcessExecutionPlanInterpreterOS
    implements AgentRunExecutionPlanInterpreter {

  private final ProcessDefinitionReader processDefinitionReader;
  private final OptimizeOpenSearchClient osClient;
  private final ProcessQueryFilterEnhancerOS queryFilterEnhancer;
  private final ProcessGroupByInterpreterFacadeOS groupByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public AgentRunExecutionPlanInterpreterOS(
      final ProcessDefinitionReader processDefinitionReader,
      final OptimizeOpenSearchClient osClient,
      final ProcessQueryFilterEnhancerOS queryFilterEnhancer,
      final ProcessGroupByInterpreterFacadeOS groupByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    this.processDefinitionReader = processDefinitionReader;
    this.osClient = osClient;
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
  protected ProcessQueryFilterEnhancerOS getQueryFilterEnhancer() {
    return queryFilterEnhancer;
  }

  @Override
  protected OptimizeOpenSearchClient getOsClient() {
    return osClient;
  }

  @Override
  public ProcessGroupByInterpreterFacadeOS getGroupByInterpreter() {
    return groupByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
  }
}
