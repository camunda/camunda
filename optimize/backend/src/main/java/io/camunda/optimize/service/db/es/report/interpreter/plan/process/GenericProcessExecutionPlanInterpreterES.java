/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.process;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.interpreter.plan.process.GenericProcessExecutionPlanInterpreter;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class GenericProcessExecutionPlanInterpreterES
    extends AbstractProcessExecutionPlanInterpreterES
    implements GenericProcessExecutionPlanInterpreter {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(GenericProcessExecutionPlanInterpreterES.class);
  private final ProcessDefinitionReader processDefinitionReader;
  private final OptimizeElasticsearchClient esClient;
  private final ProcessQueryFilterEnhancerES queryFilterEnhancer;
  private final ProcessGroupByInterpreterFacadeES groupByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;

  public GenericProcessExecutionPlanInterpreterES(
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

  public ProcessDefinitionReader getProcessDefinitionReader() {
    return this.processDefinitionReader;
  }

  public OptimizeElasticsearchClient getEsClient() {
    return this.esClient;
  }

  public ProcessQueryFilterEnhancerES getQueryFilterEnhancer() {
    return this.queryFilterEnhancer;
  }

  public ProcessGroupByInterpreterFacadeES getGroupByInterpreter() {
    return this.groupByInterpreter;
  }

  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }
}
