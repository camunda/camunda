/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationDto;
import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;


public class UserTaskImportIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_2 = "userTask2";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  private ObjectMapper objectMapper;

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Before
  public void setUp() throws Exception {
    if (objectMapper == null) {
      objectMapper = new ObjectMapperFactory(
        new OptimizeDateTimeFormatterFactory().getObject(),
        new ConfigurationService()
      ).createOptimizeMapper();
    }
  }

  @Test
  public void completedUserTasksAreImported() throws IOException {
    // given
    deployAndStartTwoUserTasksProcess();
    engineRule.finishAllUserTasks();
    engineRule.finishAllUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      assertThat(processInstanceDto.getUserTasks().size(), is(2));
      assertThat(
        processInstanceDto.getUserTasks().stream().map(UserTaskInstanceDto::getActivityId).collect(toList()),
        containsInAnyOrder(USER_TASK_1, USER_TASK_2)

      );
      processInstanceDto.getUserTasks().stream().forEach(simpleUserTaskInstanceDto -> {
        assertThat(simpleUserTaskInstanceDto.getId(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getActivityId(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getActivityInstanceId(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getStartDate(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getEndDate(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getDueDate(), is(nullValue()));
        assertThat(simpleUserTaskInstanceDto.getDeleteReason(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getTotalDurationInMs(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getIdleDurationInMs(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getWorkDurationInMs(), is(notNullValue()));
        assertThat(simpleUserTaskInstanceDto.getUserOperations(), is(notNullValue()));
      });
    }
  }

  @Test
  public void runningUserTaskIsImported() throws IOException {
    // given (two user tasks, one is started)
    deployAndStartTwoUserTasksProcess();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
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
        assertThat(userTask.getTotalDurationInMs(), is(nullValue()));
      });
    }
  }


  @Test
  public void runningAndCompletedUserTasksAreImported() throws IOException {
    // given
    deployAndStartTwoUserTasksProcess();
    engineRule.finishAllUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
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
        } else {
          assertThat(userTask.getEndDate(), is(nullValue()));
        }
      });
    }
  }

  @Test
  public void onlyUserTasksRelatedToProcessInstancesAreImported() throws IOException {
    // given
    deployAndStartOneUserTaskProcess();
    final UUID independentUserTaskId = engineRule.createIndependentUserTask();
    engineRule.finishAllUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
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
  public void noSideEffectsByOtherProcessInstanceUserTasks() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartTwoUserTasksProcess();
    engineRule.finishAllUserTasks();
    engineRule.finishAllUserTasks();

    final ProcessInstanceEngineDto processInstanceDto2 = deployAndStartOneUserTaskProcess();
    // only first task finished
    engineRule.finishAllUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(2L));
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
  public void userOperationsAreImported() throws IOException {
    // given
    deployAndStartTwoUserTasksProcess();
    engineRule.finishAllUserTasks();
    engineRule.finishAllUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> {
          assertThat(userTask.getUserOperations().size(), is(2));
          assertThat(
            userTask.getUserOperations().stream().map(UserOperationDto::getType).collect(toList()),
            containsInAnyOrder("Claim", "Complete")
          );
          userTask.getUserOperations().stream().forEach(userOperationDto -> {
            assertThat(userOperationDto.getId(), is(notNullValue()));
            assertThat(userOperationDto.getUserId(), is(notNullValue()));
            assertThat(userOperationDto.getTimestamp(), is(notNullValue()));
            assertThat(userOperationDto.getType(), is(notNullValue()));
            assertThat(userOperationDto.getProperty(), is(notNullValue()));
            assertThat(userOperationDto.getNewValue(), is(notNullValue()));
          });
        });
    }
  }

  @Test
  public void onlyUserOperationsRelatedToProcessInstancesAreImported() throws IOException {
    // given
    deployAndStartOneUserTaskProcess();
    engineRule.createIndependentUserTask();
    engineRule.finishAllUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      assertThat(processInstanceDto.getUserTasks().size(), is(1));
      processInstanceDto.getUserTasks()
        .forEach(userTask -> assertThat(userTask.getUserOperations().size(), is(2)));
    }
  }

  @Test
  public void defaultIdleTimeOnNoClaimOperation() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineRule.completeUserTaskWithoutClaim(processInstanceDto.getId());

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(),
        ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> {
          assertThat(userTask.getIdleDurationInMs(), is(0L));
        });
    }
  }

  @Test
  public void idleTimeMetricIsCalculatedOnClaimOperationImport() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartOneUserTaskProcess();
    engineRule.finishAllUserTasks();
    final long idleDuration = 500;
    changeUserTaskIdleDuration(processInstanceDto, idleDuration);

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(),
        ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> {
          assertThat(userTask.getIdleDurationInMs(), is(idleDuration));
        });
    }
  }


  @Test
  public void defaultWorkTimeOnNoClaimOperation() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineRule.completeUserTaskWithoutClaim(processInstanceDto.getId());

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(),
        ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> {
          assertThat(userTask.getWorkDurationInMs(), is(userTask.getTotalDurationInMs()));
        });
    }
  }


  @Test
  public void workTimeMetricIsCalculatedOnClaimOperationImport() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoUserTasksProcess();
    engineRule.finishAllUserTasks();
    engineRule.finishAllUserTasks();
    final long workDuration = 500;
    changeUserTaskWorkDuration(processInstanceDto, workDuration);

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(),
        ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> {
          assertThat(userTask.getWorkDurationInMs(), is(workDuration));
        });
    }
  }

  private void changeUserTaskIdleDuration(final ProcessInstanceEngineDto processInstanceDto, final long idleDuration) {
    engineRule.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(historicUserTaskInstanceDto -> {
        try {
          engineDatabaseRule.changeUserTaskClaimOperationTimestamp(
            processInstanceDto.getId(),
            historicUserTaskInstanceDto.getId(),
            historicUserTaskInstanceDto.getStartTime().plus(idleDuration, ChronoUnit.MILLIS)
          );
        } catch (SQLException e) {
          throw new OptimizeIntegrationTestException(e);
        }
      });
  }

  private void changeUserTaskWorkDuration(final ProcessInstanceEngineDto processInstanceDto, final long workDuration) {
    engineRule.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(historicUserTaskInstanceDto -> {
        if (historicUserTaskInstanceDto.getEndTime() != null) {
          try {
            engineDatabaseRule.changeUserTaskClaimOperationTimestamp(
              processInstanceDto.getId(),
              historicUserTaskInstanceDto.getId(),
              historicUserTaskInstanceDto.getEndTime().minus(workDuration, ChronoUnit.MILLIS)
            );
          } catch (SQLException e) {
            throw new OptimizeIntegrationTestException(e);
          }
        }
      });
  }

  private ProcessInstanceEngineDto deployAndStartOneUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployAndStartProcess(processModel);
  }

  private ProcessInstanceEngineDto deployAndStartTwoUserTasksProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .userTask(USER_TASK_2)
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployAndStartProcess(processModel);
  }

  private SearchResponse getSearchResponseForAllDocumentsOfType(String elasticsearchType) throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(getOptimizeIndexAliasForType(elasticsearchType))
      .types(elasticsearchType)
      .source(searchSourceBuilder);

    return elasticSearchRule.getEsClient().search(searchRequest, RequestOptions.DEFAULT);
  }


}
