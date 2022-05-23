/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.importing.permutations;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.CompletedActivityInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.CompletedProcessInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.CompletedUserTaskEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.IdentityLinkLogEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.RunningActivityInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.RunningProcessInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.RunningUserTaskInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.UserOperationLogEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.VariableUpdateEngineImportMediator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.ACTIVE_STATE;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;

@Slf4j
public class RunningProcessInstanceExtendedMediatorPermutationsImportIT extends AbstractExtendedImportMediatorPermutationsIT {

  @BeforeAll
  public static void given() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartUserTaskProcess();
    engineIntegrationExtension.claimAllRunningUserTasks(processInstanceDto.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(processInstanceDto.getId(), CANDIDATE_GROUP);
  }

  @Test
  public void runningInstanceIsFullyImported() {
    runningActivityRelatedMediators().forEach(mediatorOrder -> {
      logMediatorOrder(log, mediatorOrder);

      //includes completed events in case CompletedActivityInstanceEngineImportMediator is present
      int numberOfEventsToImport = mediatorOrder.contains(CompletedActivityInstanceEngineImportMediator.class) ? 2 : 1;

      // when
      performOrderedImport(mediatorOrder);
      elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

      // then
      assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
        .hasSize(1)
        .singleElement()
        .satisfies(persistedProcessInstanceDto -> {
          // general instance sanity check
          assertThat(persistedProcessInstanceDto.getStartDate()).isNotNull();
          assertThat(persistedProcessInstanceDto.getEndDate()).isNull();
          assertThat(persistedProcessInstanceDto.getState()).isEqualTo(ACTIVE_STATE);

          assertThat(persistedProcessInstanceDto.getFlowNodeInstances())
            .hasSize(numberOfEventsToImport)
            .allSatisfy(activity -> assertThat(activity.getStartDate()).isNotNull())
            .extracting(
              FlowNodeInstanceDto::getFlowNodeId,
              FlowNodeInstanceDto::getEndDate,
              FlowNodeInstanceDto::getTotalDurationInMs
            )
            .contains(tuple(USER_TASK_1, null, null));
          assertThat(persistedProcessInstanceDto.getUserTasks())
            .hasSize(1)
            .singleElement()
            .satisfies(userTask -> {
              assertThat(userTask.getStartDate()).isNotNull();
              assertThat(userTask.getEndDate()).isNull();
              assertThat(userTask.getAssignee()).isEqualTo(DEFAULT_USERNAME);
              assertThat(userTask.getCandidateGroups()).containsOnly(CANDIDATE_GROUP);
              assertThat(userTask.getIdleDurationInMs()).isGreaterThan(0L);
              assertThat(userTask.getWorkDurationInMs()).isNull();
              assertThat(userTask.getAssigneeOperations()).hasSize(1);
            });
        });

      final List<CamundaActivityEventDto> allStoredCamundaActivityEventsForDefinition =
        elasticSearchIntegrationTestExtension.getAllStoredCamundaActivityEventsForDefinition(TEST_PROCESS);
      // the process instance start and the single running user task event
      // + startEvent in case CompletedActivityInstanceEngineImportMediator is present
      assertThat(allStoredCamundaActivityEventsForDefinition).hasSize(1 + numberOfEventsToImport);
    });
  }

  private static Stream<List<Class<? extends ImportMediator>>> runningActivityRelatedMediators() {
    return getMediatorPermutationsStream(
      ImmutableList.of(
        RunningActivityInstanceEngineImportMediator.class,
        RunningUserTaskInstanceEngineImportMediator.class,
        RunningProcessInstanceEngineImportMediator.class,
        VariableUpdateEngineImportMediator.class,
        IdentityLinkLogEngineImportMediator.class
      ),
      Lists.newArrayList(
        CompletedActivityInstanceEngineImportMediator.class,
        CompletedUserTaskEngineImportMediator.class,
        CompletedProcessInstanceEngineImportMediator.class,
        UserOperationLogEngineImportMediator.class
      )
    );
  }

}
