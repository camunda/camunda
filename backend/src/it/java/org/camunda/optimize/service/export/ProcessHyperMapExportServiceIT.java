/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;

import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ProcessHyperMapExportServiceIT {

  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_2 = "userTask2";
  private static final String SECOND_USER = "secondUser";
  private static final String SECOND_USERS_PASSWORD = "fooPassword";
  private static final String USER_TASK_A = "userTaskA";
  private static final String USER_TASK_B = "userTaskB";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule(engineRule.getEngineName());

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

  @Before
  public void init() {
    // create second user
    engineRule.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineRule.grantAllAuthorizations(SECOND_USER);
  }

  @Test
  public void hyperMapFrequencyReportHasExpectedValue() throws Exception {
    //given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithDefaultAndTaskB2WithSecondUser(processInstanceDto);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    final ProcessReportDataDto reportData = createFrequencyReport(processDefinition);
    String reportId = createNewSingleMapReport(reportData);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithReplacedNewlinesAsString(
        "/csv/process/hyper/usertask_frequency_group_by_assignee_by_usertask.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void hyperMapDurationReportHasExpectedValue() throws Exception {
    //given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithDefaultAndTaskB2WithSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, 10L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    final ProcessReportDataDto reportData = createDurationReport(processDefinition);
    String reportId = createNewSingleMapReport(reportData);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithReplacedNewlinesAsString(
        "/csv/process/hyper/usertask_duration_group_by_assignee_by_usertask.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void reportWithEmptyResultProducesEmptyCsv() throws Exception {
    //given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    final ProcessReportDataDto reportData = createFrequencyReport(processDefinition);
    String reportId = createNewSingleMapReport(reportData);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithReplacedNewlinesAsString("/csv/process/hyper/hypermap_empty_result.csv");

    assertThat(actualContent, is(stringExpected));
  }

  private ProcessReportDataDto createFrequencyReport(final String processDefinitionKey, final String version) {
    return ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(version)
      .setReportDataType(ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE_BY_USER_TASK)
      .build();
  }

  private ProcessReportDataDto createFrequencyReport(final ProcessDefinitionEngineDto processDefinition) {
    return createFrequencyReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private ProcessReportDataDto createDurationReport(final String processDefinitionKey, final String version) {
    return ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(version)
      .setUserTaskDurationTime(UserTaskDurationTime.IDLE)
      .setReportDataType(ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_ASSIGNEE_BY_USER_TASK)
      .build();
  }

  private ProcessReportDataDto createDurationReport(final ProcessDefinitionEngineDto processDefinition) {
    return createDurationReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private void finishUserTask1AWithDefaultAndTaskB2WithSecondUser(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish user task 1 and A with default user
    engineRule.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto1.getId());
    // finish user task 2 and B with second user
    engineRule.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto1.getId());
  }

  private ProcessDefinitionEngineDto deployFourUserTasksDefinition() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
        .parallelGateway()
          .userTask(USER_TASK_1)
          .userTask(USER_TASK_2)
          .endEvent()
      .moveToLastGateway()
        .userTask(USER_TASK_A)
        .userTask(USER_TASK_B)
        .endEvent()
      .done();
    // @formatter:on
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  @SuppressWarnings("SameParameterValue")
  private void changeDuration(ProcessInstanceEngineDto processInstanceDto, long millis) {
    engineRule.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(
        historicUserTaskInstanceDto ->
        {
          try {
            engineDatabaseRule.changeUserTaskAssigneeOperationTimestamp(
              historicUserTaskInstanceDto.getId(),
              historicUserTaskInstanceDto.getStartTime().plus(millis, ChronoUnit.MILLIS)
            );
          } catch (SQLException ex) {
            throw new RuntimeException(ex);
          }
        });
  }

  private void updateSingleProcessReport(String id, SingleProcessReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }

  private String createNewSingleMapReport(ProcessReportDataDto data) {
    String singleReportId = createNewSingleReport();
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setName("FooName");
    definitionDto.setData(data);
    updateSingleProcessReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private String createNewSingleReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }
}
