/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.distributed_by.process;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static java.util.stream.Collectors.toMap;

import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.FlowNodeDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDistributedByFlowNode extends ProcessDistributedByModelElement {

  public ProcessDistributedByFlowNode(
      final ConfigurationService configurationService, final DefinitionService definitionService) {
    super(configurationService, definitionService);
  }

  @Override
  protected String getModelElementIdPath() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID;
  }

  @Override
  protected Map<String, FlowNodeDataDto> extractModelElementData(
      DefinitionOptimizeResponseDto def) {
    return ((ProcessDefinitionOptimizeDto) def)
        .getFlowNodeData().stream().collect(toMap(FlowNodeDataDto::getId, Function.identity()));
  }

  @Override
  protected ProcessReportDistributedByDto getDistributedBy() {
    return new FlowNodeDistributedByDto();
  }
}
