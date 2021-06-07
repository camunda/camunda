/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.FlowNodeDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.util.BpmnModelUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDistributedByFlowNode extends ProcessDistributedByModelElement {

  public ProcessDistributedByFlowNode(final ConfigurationService configurationService,
                                      final DefinitionService definitionService) {
    super(configurationService, definitionService);
  }

  @Override
  protected String getModelElementIdPath() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID;
  }

  @Override
  protected Map<String, String> extractModelElementNames(DefinitionOptimizeResponseDto def) {
    return BpmnModelUtil.extractFlowNodeNames(((ProcessDefinitionOptimizeDto) def).getFlowNodeData());
  }

  @Override
  protected ProcessDistributedByDto getDistributedBy() {
    return new FlowNodeDistributedByDto();
  }

}
