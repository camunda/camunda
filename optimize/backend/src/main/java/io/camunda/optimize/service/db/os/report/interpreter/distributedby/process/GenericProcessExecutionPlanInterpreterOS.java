/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.distributedby.process;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.report.filter.ProcessQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.plan.process.AbstractProcessExecutionPlanInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.interpreter.plan.process.GenericProcessExecutionPlanInterpreter;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(OpenSearchCondition.class)
public class GenericProcessExecutionPlanInterpreterOS
    extends AbstractProcessExecutionPlanInterpreterOS
    implements GenericProcessExecutionPlanInterpreter {
  @Getter private final ProcessDefinitionReader processDefinitionReader;
  @Getter private final OptimizeOpenSearchClient osClient;
  @Getter private final ProcessQueryFilterEnhancerOS queryFilterEnhancer;
  @Getter private final ProcessGroupByInterpreterFacadeOS groupByInterpreter;
  @Getter private final ProcessViewInterpreterFacadeOS viewInterpreter;
}
