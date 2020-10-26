/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.UserTaskDistributedByDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ACTIVITY_ID;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDistributedByUserTask extends ProcessDistributedByModelElement {

  public ProcessDistributedByUserTask(final ConfigurationService configurationService,
                                      final DefinitionService definitionService) {
    super(configurationService, definitionService);
  }

  @Override
  protected String getModelElementIdPath() {
    return USER_TASKS + "." + USER_TASK_ACTIVITY_ID;
  }

  @Override
  protected Map<String, String> extractModelElementNames(DefinitionOptimizeResponseDto def) {
    return ((ProcessDefinitionOptimizeDto) def).getUserTaskNames();
  }

  @Override
  protected ProcessDistributedByDto getDistributedBy() {
    return new UserTaskDistributedByDto();
  }

}
