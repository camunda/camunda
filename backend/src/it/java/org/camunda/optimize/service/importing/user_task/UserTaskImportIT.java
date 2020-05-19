/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.user_task;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.UserTaskInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.StringBody.subString;

public class UserTaskImportIT extends AbstractUserTaskImportIT {

  @Test
  public void completedUserTasksAreImported() {
    // given
    deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .allSatisfy(processInstanceDto -> {
        assertThat(processInstanceDto.getUserTasks()).hasSize(2);
        assertThat(
          processInstanceDto.getUserTasks().stream().map(UserTaskInstanceDto::getActivityId).collect(toList()))
          .containsExactlyInAnyOrder(USER_TASK_1, USER_TASK_2);
        processInstanceDto.getUserTasks().forEach(simpleUserTaskInstanceDto -> {
          assertThat(simpleUserTaskInstanceDto.getId()).isNotNull();
          assertThat(simpleUserTaskInstanceDto.getActivityId()).isNotNull();
          assertThat(simpleUserTaskInstanceDto.getActivityInstanceId()).isNotNull();
          assertThat(simpleUserTaskInstanceDto.getStartDate()).isNotNull();
          assertThat(simpleUserTaskInstanceDto.getEndDate()).isNotNull();
          assertThat(simpleUserTaskInstanceDto.getDueDate()).isNull();
          assertThat(simpleUserTaskInstanceDto.getClaimDate()).isNotNull();
          assertThat(simpleUserTaskInstanceDto.getDeleteReason()).isNotNull();
          assertThat(simpleUserTaskInstanceDto.getTotalDurationInMs()).isNotNull();
          assertThat(simpleUserTaskInstanceDto.getIdleDurationInMs()).isNotNull();
          assertThat(simpleUserTaskInstanceDto.getWorkDurationInMs()).isNotNull();
          assertThat(simpleUserTaskInstanceDto.getAssigneeOperations()).isNotNull();
        });
      });
  }

  @Test
  public void runningUserTaskIsImported() {
    // given (two user tasks, one is started)
    deployAndStartTwoUserTasksProcess();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .allSatisfy(processInstanceDto -> {
        assertThat(processInstanceDto.getUserTasks()).hasSize(1);
        assertThat(
          processInstanceDto.getUserTasks().stream().map(UserTaskInstanceDto::getActivityId).collect(toList()))
          .containsExactlyInAnyOrder(USER_TASK_1);
        processInstanceDto.getUserTasks().forEach(userTask -> {
          assertThat(userTask.getId()).isNotNull();
          assertThat(userTask.getActivityId()).isNotNull();
          assertThat(userTask.getActivityInstanceId()).isNotNull();
          assertThat(userTask.getStartDate()).isNotNull();
          assertThat(userTask.getEndDate()).isNull();
          assertThat(userTask.getClaimDate()).isNull();
          assertThat(userTask.getTotalDurationInMs()).isNull();
        });
      });
  }

  @Test
  public void runningAndCompletedUserTasksAreImported() throws IOException {
    // given
    deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .allSatisfy(processInstanceDto -> {
        assertThat(processInstanceDto.getUserTasks()).hasSize(2);
        assertThat(
          processInstanceDto.getUserTasks().stream().map(UserTaskInstanceDto::getActivityId).collect(toList()))
          .containsExactlyInAnyOrder(USER_TASK_1, USER_TASK_2);
        processInstanceDto.getUserTasks().forEach(userTask -> {
          if (USER_TASK_1.equals(userTask.getActivityId())) {
            assertThat(userTask.getEndDate()).isNotNull();
            assertThat(userTask.getClaimDate()).isNotNull();
          } else {
            assertThat(userTask.getEndDate()).isNull();
            assertThat(userTask.getClaimDate()).isNull();
          }
        });
      });
  }

  @Test
  public void runningAndCompletedUserTasksAreImported_despiteEsUpdateFailures() {
    // given
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances()).isEmpty();

    // given ES update request fails
    deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest userTaskImportMatcher = request()
      .withPath("/_bulk")
      .withMethod(POST)
      .withBody(subString("\"_index\":\"" + embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix() + "-" + PROCESS_INSTANCE_INDEX_NAME + "\""));
    esMockServer
      .when(userTaskImportMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then expected user tasks are stored on next successful update
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .allSatisfy(processInstanceDto -> {
        assertThat(processInstanceDto.getUserTasks()).hasSize(2);
        assertThat(
          processInstanceDto.getUserTasks().stream().map(UserTaskInstanceDto::getActivityId).collect(toList()))
          .containsExactlyInAnyOrder(USER_TASK_1, USER_TASK_2);
        processInstanceDto.getUserTasks().forEach(userTask -> {
          if (USER_TASK_1.equals(userTask.getActivityId())) {
            assertThat(userTask.getEndDate()).isNotNull();
            assertThat(userTask.getClaimDate()).isNotNull();
          } else {
            assertThat(userTask.getEndDate()).isNull();
            assertThat(userTask.getClaimDate()).isNull();
          }
        });
      });
    esMockServer.verify(userTaskImportMatcher);
  }

  @Test
  public void onlyUserTasksRelatedToProcessInstancesAreImported() throws IOException {
    // given
    deployAndStartOneUserTaskProcess();
    final UUID independentUserTaskId = engineIntegrationExtension.createIndependentUserTask();
    engineIntegrationExtension.finishAllRunningUserTasks();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .allSatisfy(processInstanceDto -> {
        assertThat(processInstanceDto.getUserTasks()).hasSize(1);
        assertThat(
          processInstanceDto.getUserTasks().stream().map(UserTaskInstanceDto::getActivityId).collect(toList()))
          .containsExactlyInAnyOrder(USER_TASK_1);
        assertThat(
          processInstanceDto.getUserTasks().stream().map(UserTaskInstanceDto::getId).collect(toList()))
          .doesNotContain(independentUserTaskId.toString());
      });
  }

  @Test
  public void importFinishesIfIndependentRunningUserTasksExist() throws IOException {
    // given
    engineIntegrationExtension.createIndependentUserTask();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances()).isEmpty();
  }

  @Test
  public void noSideEffectsByOtherProcessInstanceUserTasks() {
    // given
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    final ProcessInstanceEngineDto processInstanceDto2 = deployAndStartOneUserTaskProcess();
    // only first task finished
    engineIntegrationExtension.finishAllRunningUserTasks();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(2)
      .allSatisfy(persistedProcessInstanceDto -> {
        if (persistedProcessInstanceDto.getProcessInstanceId().equals(processInstanceDto1.getId())) {
          assertThat(persistedProcessInstanceDto.getUserTasks()).hasSize(2);
          assertThat(
            persistedProcessInstanceDto.getUserTasks()
              .stream()
              .map(UserTaskInstanceDto::getActivityId)
              .collect(toList()))
            .containsExactlyInAnyOrder(USER_TASK_1, USER_TASK_2);
        } else {
          assertThat(persistedProcessInstanceDto.getUserTasks()).hasSize(1);
          assertThat(
            persistedProcessInstanceDto.getUserTasks()
              .stream()
              .map(UserTaskInstanceDto::getActivityId)
              .collect(toList()))
            .containsExactlyInAnyOrder(USER_TASK_1);
        }
      });
  }

  @Test
  public void importFinishesIfIndependentCompletesUserTasksWithOperationsExist() throws IOException {
    // given
    engineIntegrationExtension.createIndependentUserTask();
    engineIntegrationExtension.finishAllRunningUserTasks();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances()).isEmpty();
  }

  @Test
  public void defaultIdleTimeOnNoClaimOperation() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceDto.getId());

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .allSatisfy(persistedProcessInstanceDto -> {
        persistedProcessInstanceDto.getUserTasks().forEach(userTask -> {
          if (USER_TASK_1.equals(userTask.getActivityId())) {
            assertThat(userTask.getIdleDurationInMs()).isEqualTo(0L);
          } else if (USER_TASK_2.equals(userTask.getActivityId())) {
            assertThat(userTask.getIdleDurationInMs()).isNull();
          }
          assertThat(userTask.getClaimDate()).isNull();
        });
      });
  }

  @Test
  public void idleTimeMetricIsCalculatedOnClaimOperationImport() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartOneUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    final long idleDuration = 500;
    changeUserTaskIdleDuration(processInstanceDto, idleDuration);

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .allSatisfy(persistedProcessInstanceDto -> {
        persistedProcessInstanceDto.getUserTasks()
          .forEach(userTask -> assertThat(userTask.getIdleDurationInMs()).isEqualTo(idleDuration));
      });
  }

  @Test
  public void defaultWorkTimeOnNoClaimOperation() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceDto.getId());

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .allSatisfy(persistedProcessInstanceDto -> {
        persistedProcessInstanceDto.getUserTasks()
          .forEach(userTask -> assertThat(userTask.getWorkDurationInMs()).isEqualTo(userTask.getTotalDurationInMs()));
      });
  }

  @Test
  public void workTimeMetricIsCalculatedOnClaimOperationImport() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    final long workDuration = 500;
    changeUserTaskWorkDuration(processInstanceDto, workDuration);

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .allSatisfy(persistedProcessInstanceDto -> {
        persistedProcessInstanceDto.getUserTasks()
          .forEach(userTask -> assertThat(userTask.getWorkDurationInMs()).isEqualTo(workDuration));
      });
  }

}
