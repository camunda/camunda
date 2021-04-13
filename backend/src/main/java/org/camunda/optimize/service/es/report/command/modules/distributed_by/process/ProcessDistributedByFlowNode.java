/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.FlowNodeDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDistributedByFlowNode extends ProcessDistributedByModelElement {

  public ProcessDistributedByFlowNode(final ConfigurationService configurationService,
                                      final DefinitionService definitionService) {
    super(configurationService, definitionService);
  }

  @Override
  protected String getModelElementIdPath() {
    return EVENTS + "." + ACTIVITY_ID;
  }

  @Override
  protected Map<String, String> extractModelElementNames(DefinitionOptimizeResponseDto def) {
    return ((ProcessDefinitionOptimizeDto) def).getFlowNodeData().stream()
      .collect(Collectors.toMap(FlowNodeDataDto::getId, FlowNodeDataDto::getName));
  }

  @Override
  protected ProcessDistributedByDto getDistributedBy() {
    return new FlowNodeDistributedByDto();
  }

}
