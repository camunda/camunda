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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class GenericProcessExecutionPlanInterpreterES
    extends AbstractProcessExecutionPlanInterpreterES
    implements GenericProcessExecutionPlanInterpreter {
  @Getter private final ProcessDefinitionReader processDefinitionReader;
  @Getter private final OptimizeElasticsearchClient esClient;
  @Getter private final ProcessQueryFilterEnhancerES queryFilterEnhancer;
  @Getter private final ProcessGroupByInterpreterFacadeES groupByInterpreter;
  @Getter private final ProcessViewInterpreterFacadeES viewInterpreter;
}
