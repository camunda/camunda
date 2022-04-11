/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.UserTaskDistributedByDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDistributedByUserTask extends ProcessDistributedByModelElement {

  public ProcessDistributedByUserTask(final ConfigurationService configurationService,
                                      final DefinitionService definitionService) {
    super(configurationService, definitionService);
  }

  @Override
  protected String getModelElementIdPath() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID;
  }

  @Override
  protected Map<String, FlowNodeDataDto> extractModelElementData(DefinitionOptimizeResponseDto def) {
    return ((ProcessDefinitionOptimizeDto) def).getUserTaskData()
      .stream()
      .collect(toMap(FlowNodeDataDto::getId, Function.identity()));
  }

  @Override
  protected ProcessReportDistributedByDto getDistributedBy() {
    return new UserTaskDistributedByDto();
  }

}
