/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.user_task;

import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.UserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.engine.EngineImportScheduler;
import org.camunda.optimize.service.importing.engine.mediator.CompletedActivityInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.CompletedProcessInstanceEngineImportMediator;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_2;
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
    importAllEngineEntitiesFromScratch();

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
    importAllEngineEntitiesFromScratch();

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
  public void canceledUserTaskIsImported() {
    // given
    final ProcessInstanceEngineDto processInstance = deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.cancelActivityInstance(processInstance.getId(), USER_TASK_1);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(processInstanceDto -> assertThat(processInstanceDto.getUserTasks())
        .singleElement()
        .satisfies(task -> {
          assertThat(task.getActivityId()).isEqualTo(USER_TASK_1);
          assertThat(task.getId()).isNotNull();
          assertThat(task.getActivityId()).isNotNull();
          assertThat(task.getStartDate()).isNotNull();
          assertThat(task.getEndDate()).isNotNull();
          assertThat(task.getCanceled()).isTrue();
        }));
  }

  @Test
  public void canceledUserTaskDoesNotAffectOtherInstance() {
    // given
    final ProcessInstanceEngineDto firstInstance = deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.cancelActivityInstance(firstInstance.getId(), USER_TASK_1);
    final ProcessInstanceEngineDto secondInstance =
      engineIntegrationExtension.startProcessInstance(firstInstance.getDefinitionId());

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessInstanceDto> allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(allProcessInstances).hasSize(2);
    assertThat(allProcessInstances)
      .filteredOn(savedInstance -> savedInstance.getProcessInstanceId().equals(firstInstance.getId()))
      .singleElement()
      .satisfies(instance -> assertThat(instance.getUserTasks()).hasSize(1).singleElement().satisfies(task -> {
        assertThat(task.getActivityId()).isEqualTo(USER_TASK_1);
        assertThat(task.getCanceled()).isTrue();
      }));
    assertThat(allProcessInstances)
      .filteredOn(savedInstance -> savedInstance.getProcessInstanceId().equals(secondInstance.getId()))
      .singleElement()
      .satisfies(instance -> assertThat(instance.getUserTasks()).hasSize(1).singleElement().satisfies(task -> {
        assertThat(task.getActivityId()).isEqualTo(USER_TASK_1);
        // The task has not completed so it been marked as not canceled
        assertThat(task.getCanceled()).isNull();
      }));
  }

  @Test
  public void canceledAndCompletedUserTasksAreImported() {
    // given
    final ProcessInstanceEngineDto processInstance = deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.cancelActivityInstance(processInstance.getId(), USER_TASK_2);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getUserTasks()).hasSize(2);
        assertThat(
          processInstanceDto.getUserTasks().stream().map(UserTaskInstanceDto::getActivityId).collect(toList()))
          .containsExactlyInAnyOrder(USER_TASK_1, USER_TASK_2);
        processInstanceDto.getUserTasks().forEach(simpleUserTaskInstanceDto -> {
          assertThat(simpleUserTaskInstanceDto.getId()).isNotNull();
          assertThat(simpleUserTaskInstanceDto.getActivityId()).isNotNull();
          assertThat(simpleUserTaskInstanceDto.getStartDate()).isNotNull();
          assertThat(simpleUserTaskInstanceDto.getEndDate()).isNotNull();
        });
        assertThat(processInstanceDto.getUserTasks())
          .extracting(UserTaskInstanceDto::getActivityId, UserTaskInstanceDto::getCanceled)
          .containsExactlyInAnyOrder(
            Tuple.tuple(USER_TASK_1, false),
            Tuple.tuple(USER_TASK_2, true)
          );
      });
  }

  @Test
  public void runningAndCompletedUserTasksAreImported() {
    // given
    deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();

    // when
    importAllEngineEntitiesFromScratch();

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
    importAllEngineEntitiesFromScratch();

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
        .getIndexPrefix() + "-" + PROCESS_INSTANCE_INDEX_PREFIX));
    esMockServer
      .when(userTaskImportMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    importAllEngineEntitiesFromLastIndex();

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
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram());
    final UUID independentUserTaskId = engineIntegrationExtension.createIndependentUserTask();
    engineIntegrationExtension.finishAllRunningUserTasks();

    // when
    importAllEngineEntitiesFromScratch();

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
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances()).isEmpty();
  }

  @Test
  public void noSideEffectsByOtherProcessInstanceUserTasks() {
    // given
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getSingleUserTaskDiagram());
    // only first task finished
    engineIntegrationExtension.finishAllRunningUserTasks();

    // when
    importAllEngineEntitiesFromScratch();

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
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances()).isEmpty();
  }

  @Test
  public void defaultIdleTimeOnNoClaimOperation() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceDto.getId());

    // when
    importAllEngineEntitiesFromScratch();

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
  public void idleTimeMetricIsCalculatedOnClaimOperationImport() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getSingleUserTaskDiagram());
    engineIntegrationExtension.finishAllRunningUserTasks();
    final long idleDuration = 500;
    changeUserTaskIdleDuration(processInstanceDto, idleDuration);

    // when
    importAllEngineEntitiesFromScratch();

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
  public void defaultWorkTimeOnNoClaimOperation() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceDto.getId());

    // when
    importAllEngineEntitiesFromScratch();

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
  public void workTimeMetricIsCalculatedOnClaimOperationImport() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    final long workDuration = 500;
    changeUserTaskWorkDuration(processInstanceDto, workDuration);

    // when
    importAllEngineEntitiesFromScratch();

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

  @ParameterizedTest
  @MethodSource("allUserTaskReports")
  public void userTaskFrequencyReportsCanBeEvaluatedWithOnlyCancellationUserTaskDataImported(ProcessReportDataType reportDataType) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(processInstance.getId(), USER_TASK_1);

    importCompletedActivityAndProcessInstances();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinition.getKey())
      .setProcessDefinitionVersion(processDefinition.getVersionAsString())
      .setReportDataType(reportDataType)
      .setDistributeByDateInterval(AggregateByDateUnit.AUTOMATIC)
      .setGroupByDateInterval(AggregateByDateUnit.AUTOMATIC)
      .build();
    final ReportResultResponseDto<Object> result = reportClient.evaluateReport(reportData).getResult();

    // then the report can be evaluated even though user task only contains cancellation data
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
  }

  private static List<ProcessReportDataType> allUserTaskReports() {
    return Arrays.stream(
      ProcessReportDataType.values())
      .filter(type -> type.name().toLowerCase().startsWith("user_task_"))
      .collect(Collectors.toList()
      );
  }

  @SneakyThrows
  private void importCompletedActivityAndProcessInstances() {
    for (EngineImportScheduler scheduler : embeddedOptimizeExtension.getImportSchedulerManager()
      .getImportSchedulers()) {
      final List<EngineImportMediator> mediators = scheduler.getImportMediators()
        .stream()
        .filter(mediator -> CompletedActivityInstanceEngineImportMediator.class.equals(mediator.getClass()) ||
          CompletedProcessInstanceEngineImportMediator.class.equals(mediator.getClass()))
        .collect(Collectors.toList());
      for (EngineImportMediator mediator : mediators) {
        mediator.runImport().get(10, TimeUnit.SECONDS);
      }
    }
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

}
