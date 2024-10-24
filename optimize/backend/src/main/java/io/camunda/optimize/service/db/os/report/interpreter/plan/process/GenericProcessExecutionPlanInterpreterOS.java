/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.plan.process;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.report.filter.ProcessQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.interpreter.plan.process.GenericProcessExecutionPlanInterpreter;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class GenericProcessExecutionPlanInterpreterOS
    extends AbstractProcessExecutionPlanInterpreterOS
    implements GenericProcessExecutionPlanInterpreter {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(GenericProcessExecutionPlanInterpreterOS.class);
  private final ProcessDefinitionReader processDefinitionReader;
  private final OptimizeOpenSearchClient osClient;
  private final ProcessQueryFilterEnhancerOS queryFilterEnhancer;
  private final ProcessGroupByInterpreterFacadeOS groupByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public GenericProcessExecutionPlanInterpreterOS(
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

  public ProcessDefinitionReader getProcessDefinitionReader() {
    return this.processDefinitionReader;
  }

  public OptimizeOpenSearchClient getOsClient() {
    return this.osClient;
  }

  public ProcessQueryFilterEnhancerOS getQueryFilterEnhancer() {
    return this.queryFilterEnhancer;
  }

  public ProcessGroupByInterpreterFacadeOS getGroupByInterpreter() {
    return this.groupByInterpreter;
  }

  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return this.viewInterpreter;
  }
}
