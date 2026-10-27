/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.model;

import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_FLOW_NODE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static java.util.stream.Collectors.toMap;

import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.interpreter.distributedby.process.model.ProcessDistributedByModelElementInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessDistributedByFlowNodeInterpreterOS
    extends AbstractProcessDistributedByModelElementInterpreterOS {
  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;
  private final ProcessDistributedByModelElementInterpreterHelper helper;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public ProcessDistributedByFlowNodeInterpreterOS(
      final ConfigurationService configurationService,
      final DefinitionService definitionService,
      final ProcessDistributedByModelElementInterpreterHelper helper,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    super();
    this.configurationService = configurationService;
    this.definitionService = definitionService;
    this.helper = helper;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  protected ConfigurationService getConfigurationService() {
    return configurationService;
  }

  @Override
  protected DefinitionService getDefinitionService() {
    return definitionService;
  }

  @Override
  protected ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
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
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return Set.of(PROCESS_DISTRIBUTED_BY_FLOW_NODE);
  }
}
