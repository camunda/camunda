/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.ProcessGoalDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessGoalService {

  private final ProcessDefinitionReader processDefinitionReader;
  private final DataSourceDefinitionAuthorizationService dataSourceDefinitionAuthorizationService;

  public List<ProcessGoalDto> getProcessDefinitionGoals(String userId) {
    List<ProcessGoalDto> processGoalDtos = new ArrayList<>();
    processDefinitionReader.getAllProcessDefinitions().forEach(processDefinition -> {
      if (dataSourceDefinitionAuthorizationService.isAuthorizedToAccessDefinition(userId, processDefinition)) {
        processGoalDtos.add(new ProcessGoalDto(
          processDefinition.getKey(),
          processDefinition.getName(),
          Collections.emptyList(),
          null
        ));
      }
    });
    return processGoalDtos;
  }

}
