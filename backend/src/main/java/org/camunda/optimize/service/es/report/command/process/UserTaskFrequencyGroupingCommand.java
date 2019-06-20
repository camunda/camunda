/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process;


import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;

import java.util.Map;
import java.util.function.Function;

public abstract class UserTaskFrequencyGroupingCommand extends FlowNodeFrequencyGroupingCommand {

  @Override
  protected Function<ProcessDefinitionOptimizeDto, Map<String, String>> getGetFlowNodeNameExtractor() {
    return ProcessDefinitionOptimizeDto::getUserTaskNames;
  }
}
