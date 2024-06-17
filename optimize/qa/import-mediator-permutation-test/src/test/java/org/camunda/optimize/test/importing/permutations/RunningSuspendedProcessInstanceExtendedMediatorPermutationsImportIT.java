/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.importing.permutations;

import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static io.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import io.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.CompletedActivityInstanceEngineImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.CompletedProcessInstanceEngineImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.CompletedUserTaskEngineImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.RunningActivityInstanceEngineImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.RunningProcessInstanceEngineImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.RunningUserTaskInstanceEngineImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.UserOperationLogEngineImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.VariableUpdateEngineImportMediator;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Slf4j
public class RunningSuspendedProcessInstanceExtendedMediatorPermutationsImportIT
    extends AbstractExtendedImportMediatorPermutationsIT {

  @Test
  public void runningSuspendedInstanceIsFullyImported() {
    runningActivityRelatedMediators()
        .forEach(
            mediatorOrder -> {
              logMediatorOrder(log, mediatorOrder);

              // includes completed events in case CompletedActivityInstanceEngineImportMediator is
              // present
              int numberOfEventsToImport =
                  mediatorOrder.contains(CompletedActivityInstanceEngineImportMediator.class)
                      ? 2
                      : 1;

              // when
              performOrderedImport(mediatorOrder);
              databaseIntegrationTestExtension.refreshAllOptimizeIndices();

              // then
              assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
                  .hasSize(1)
                  .singleElement()
                  .satisfies(
                      persistedProcessInstanceDto -> {
                        // general instance sanity check
                        assertThat(persistedProcessInstanceDto.getStartDate()).isNotNull();
                        assertThat(persistedProcessInstanceDto.getEndDate()).isNull();
                        assertThat(persistedProcessInstanceDto.getState())
                            .isEqualTo(SUSPENDED_STATE);
                        assertThat(persistedProcessInstanceDto.getFlowNodeInstances())
                            .hasSize(numberOfEventsToImport)
                            .allSatisfy(activity -> assertThat(activity.getStartDate()).isNotNull())
                            .extracting(
                                FlowNodeInstanceDto::getFlowNodeId,
                                FlowNodeInstanceDto::getEndDate,
                                FlowNodeInstanceDto::getTotalDurationInMs)
                            .contains(tuple(USER_TASK_1, null, null));
                        assertThat(persistedProcessInstanceDto.getUserTasks())
                            .hasSize(1)
                            .singleElement()
                            .satisfies(
                                userTask -> {
                                  assertThat(userTask.getStartDate()).isNotNull();
                                  assertThat(userTask.getEndDate()).isNull();
                                  assertThat(userTask.getIdleDurationInMs()).isNull();
                                  assertThat(userTask.getWorkDurationInMs()).isNull();
                                });
                      });

              final List<CamundaActivityEventDto> allStoredCamundaActivityEventsForDefinition =
                  databaseIntegrationTestExtension.getAllStoredCamundaActivityEventsForDefinition(
                      TEST_PROCESS);
              // the process instance start and the single running user task event
              // + startEvent in case CompletedActivityInstanceEngineImportMediator is present
              assertThat(allStoredCamundaActivityEventsForDefinition)
                  .hasSize(1 + numberOfEventsToImport);
            });
  }

  @BeforeAll
  public static void given() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartUserTaskProcess();
    engineIntegrationExtension.suspendProcessInstanceByInstanceId(processInstanceDto.getId());
  }

  private static Stream<List<Class<? extends ImportMediator>>> runningActivityRelatedMediators() {
    return getMediatorPermutationsStream(
        ImmutableList.of(
            RunningActivityInstanceEngineImportMediator.class,
            RunningUserTaskInstanceEngineImportMediator.class,
            RunningProcessInstanceEngineImportMediator.class,
            VariableUpdateEngineImportMediator.class,
            UserOperationLogEngineImportMediator.class),
        Lists.newArrayList(
            CompletedActivityInstanceEngineImportMediator.class,
            CompletedUserTaskEngineImportMediator.class,
            CompletedProcessInstanceEngineImportMediator.class,
            UserOperationLogEngineImportMediator.class));
  }
}
