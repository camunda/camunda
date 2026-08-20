/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.model;

import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static java.util.stream.Collectors.toMap;

import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.interpreter.distributedby.process.model.ProcessDistributedByModelElementInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessDistributedByFlowNodeInterpreterES
    extends AbstractProcessDistributedByModelElementInterpreterES {

  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;
  private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final ProcessDistributedByModelElementInterpreterHelper helper;

  public ProcessDistributedByFlowNodeInterpreterES(
      final ConfigurationService configurationService,
      final DefinitionService definitionService,
      final ProcessViewInterpreterFacadeES viewInterpreter,
      final ProcessDistributedByModelElementInterpreterHelper helper) {
    this.configurationService = configurationService;
    this.definitionService = definitionService;
    this.viewInterpreter = viewInterpreter;
    this.helper = helper;
  }

  @Override
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return Set.of(PROCESS_DISTRIBUTED_BY_FLOW_NODE);
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  @Override
  public DefinitionService getDefinitionService() {
    return definitionService;
  }

  @Override
  protected ProcessDistributedByModelElementInterpreterHelper getHelper() {
    return helper;
  }

  @Override
  protected String getModelElementIdPath() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID;
  }

  @Override
  protected Map<String, FlowNodeDataDto> extractModelElementData(
      final DefinitionOptimizeResponseDto def) {
    return ((ProcessDefinitionOptimizeDto) def)
        .getFlowNodeData().stream().collect(toMap(FlowNodeDataDto::getId, Function.identity()));
  }

  @Override
  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return viewInterpreter;
  }
}
