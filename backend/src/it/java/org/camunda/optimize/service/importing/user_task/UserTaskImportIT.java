/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.user_task;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.UserTaskInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;

public class UserTaskImportIT extends AbstractUserTaskImportIT {

  @Test
  public void completedUserTasksAreImported() throws IOException {
    // given
    deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      assertThat(processInstanceDto.getUserTasks().size(), is(2));
      assertThat(
        processInstanceDto.getUserTasks().stream().map(UserTaskInstanceDto::getActivityId).collect(toList()),
        containsInAnyOrder(USER_TASK_1, USER_TASK_2)

      );
      processInstanceDto.getUserTasks().forEach(simpleUserTaskInstanceDto -> {
        assertThat(simpleUserTaskInstanceDto.getId(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getActivityId(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getActivityInstanceId(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getStartDate(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getEndDate(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getDueDate(), is(nullValue()));
        assertThat(simpleUserTaskInstanceDto.getClaimDate(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getDeleteReason(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getTotalDurationInMs(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getIdleDurationInMs(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getWorkDurationInMs(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getAssigneeOperations(), is(notNullValue()));
      });
    }
  }

  @Test
  public void runningUserTaskIsImported() throws IOException {
    // given (two user tasks, one is started)
    deployAndStartTwoUserTasksProcess();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      assertThat(processInstanceDto.getUserTasks().size(), is(1));
      assertThat(
        processInstanceDto.getUserTasks().stream().map(UserTaskInstanceDto::getActivityId).collect(toList()),
        containsInAnyOrder(USER_TASK_1)
      );

      processInstanceDto.getUserTasks().forEach(userTask -> {
        assertThat(userTask.getId(), is(notNullValue()));
        assertThat(userTask.getActivityId(), is(notNullValue()));
        assertThat(userTask.getActivityInstanceId(), is(notNullValue()));
        assertThat(userTask.getStartDate(), is(notNullValue()));
        assertThat(userTask.getEndDate(), is(nullValue()));
        assertThat(userTask.getClaimDate(), is(nullValue()));
        assertThat(userTask.getTotalDurationInMs(), is(nullValue()));
      });
    }
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
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      assertThat(processInstanceDto.getUserTasks().size(), is(2));
      assertThat(
        processInstanceDto.getUserTasks().stream().map(UserTaskInstanceDto::getActivityId).collect(toList()),
        containsInAnyOrder(USER_TASK_1, USER_TASK_2)
      );

      processInstanceDto.getUserTasks().forEach(userTask -> {
        if (USER_TASK_1.equals(userTask.getActivityId())) {
          assertThat(userTask.getEndDate(), is(notNullValue()));
          assertThat(userTask.getClaimDate(), is(notNullValue()));
        } else {
          assertThat(userTask.getEndDate(), is(nullValue()));
          assertThat(userTask.getClaimDate(), is(nullValue()));
        }
      });
    }
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
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      assertThat(processInstanceDto.getUserTasks().size(), is(1));
      assertThat(
        processInstanceDto.getUserTasks().stream().map(UserTaskInstanceDto::getActivityId).collect(toList()),
        containsInAnyOrder(USER_TASK_1)
      );
      assertThat(
        processInstanceDto.getUserTasks().stream().map(UserTaskInstanceDto::getId).collect(toList()),
        not(containsInAnyOrder(independentUserTaskId))
      );
    }
  }

  @Test
  public void importFinishesIfIndependentRunningUserTasksExist() throws IOException {
    // given
    engineIntegrationExtension.createIndependentUserTask();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(0L));
  }

  @Test
  public void noSideEffectsByOtherProcessInstanceUserTasks() throws IOException {
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
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(2L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      if (persistedProcessInstanceDto.getProcessInstanceId().equals(processInstanceDto1.getId())) {
        assertThat(persistedProcessInstanceDto.getUserTasks().size(), is(2));
        assertThat(
          persistedProcessInstanceDto.getUserTasks()
            .stream()
            .map(UserTaskInstanceDto::getActivityId)
            .collect(toList()),
          containsInAnyOrder(USER_TASK_1, USER_TASK_2)
        );
      } else {
        assertThat(persistedProcessInstanceDto.getUserTasks().size(), is(1));
        assertThat(
          persistedProcessInstanceDto.getUserTasks()
            .stream()
            .map(UserTaskInstanceDto::getActivityId)
            .collect(toList()),
          containsInAnyOrder(USER_TASK_1)
        );
      }
    }
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
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(0L));
  }

  @Test
  public void defaultIdleTimeOnNoClaimOperation() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceDto.getId());

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(),
        ProcessInstanceDto.class
      );

      persistedProcessInstanceDto.getUserTasks().forEach(userTask -> {
        if (USER_TASK_1.equals(userTask.getActivityId())) {
          assertThat(userTask.getIdleDurationInMs(), is(0L));
        } else if (USER_TASK_2.equals(userTask.getActivityId())) {
          assertThat(userTask.getIdleDurationInMs(), is(nullValue()));
        }
        assertThat(userTask.getClaimDate(), is(nullValue()));
      });
    }
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
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(),
        ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> assertThat(userTask.getIdleDurationInMs(), is(idleDuration)));
    }
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
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(),
        ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> assertThat(userTask.getWorkDurationInMs(), is(userTask.getTotalDurationInMs())));
    }
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
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(),
        ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> assertThat(userTask.getWorkDurationInMs(), is(workDuration)));
    }
  }

}
