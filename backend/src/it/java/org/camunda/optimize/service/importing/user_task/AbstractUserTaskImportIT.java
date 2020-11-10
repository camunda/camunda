/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.user_task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.junit.jupiter.api.BeforeEach;

import java.sql.SQLException;
import java.time.temporal.ChronoUnit;

import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public abstract class AbstractUserTaskImportIT extends AbstractIT {

  protected ObjectMapper objectMapper;

  @BeforeEach
  public void setUp() {
    if (objectMapper == null) {
      objectMapper = new ObjectMapperFactory(
        new OptimizeDateTimeFormatterFactory().getObject(),
        ConfigurationServiceBuilder.createDefaultConfiguration()
      ).createOptimizeMapper();
    }
  }

  protected void changeUserTaskIdleDuration(final ProcessInstanceEngineDto processInstanceDto,
                                            final long idleDuration) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(historicUserTaskInstanceDto -> {
        try {
          engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
            historicUserTaskInstanceDto.getId(),
            historicUserTaskInstanceDto.getStartTime().plus(idleDuration, ChronoUnit.MILLIS)
          );
        } catch (SQLException e) {
          throw new OptimizeIntegrationTestException(e);
        }
      });
  }

  protected void changeUserTaskWorkDuration(final ProcessInstanceEngineDto processInstanceDto,
                                            final long workDuration) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(historicUserTaskInstanceDto -> {
        if (historicUserTaskInstanceDto.getEndTime() != null) {
          try {
            engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
              historicUserTaskInstanceDto.getId(),
              historicUserTaskInstanceDto.getEndTime().minus(workDuration, ChronoUnit.MILLIS)
            );
          } catch (SQLException e) {
            throw new OptimizeIntegrationTestException(e);
          }
        }
      });
  }

  protected ProcessInstanceEngineDto deployAndStartTwoUserTasksProcess() {
    return engineIntegrationExtension.deployAndStartProcess(getDoubleUserTaskDiagram());
  }

  protected ProcessDefinitionEngineDto deployOneUserTaskDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram());
  }

}
