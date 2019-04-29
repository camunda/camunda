/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.user_task;

import com.fasterxml.jackson.core.type.TypeReference;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public abstract class AbstractUserTaskDurationByUserTaskReportEvaluationIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String PROCESS_DEFINITION_KEY = "123";
  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_2 = "userTask2";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);

    final long setDuration = 20L;
    changeDuration(processInstanceDto, setDuration);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse =
      evaluateReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(String.valueOf(processDefinition.getVersion())));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.USER_TASK));
    assertThat(resultReportDataDto.getView().getProperty(), is(getViewProperty()));

    final ProcessDurationReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
    assertThat(
      result.getDataEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurations(setDuration))
    );
    assertThat(
      result.getDataEntryForKey(USER_TASK_2).get().getValue(),
      is(calculateExpectedValueGivenDurations(setDuration))
    );

    assertThat(result.getProcessInstanceCount(), is(1L));
  }

  @Test
  public void reportEvaluationForSeveralProcesses() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    final Long[] setDurations = new Long[]{10L, 30L};
    changeDuration(processInstanceDto1, setDurations[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, setDurations[1]);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData =
      createReport(processDefinition);
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(2));
    assertThat(
      result.getDataEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurations(setDurations))
    );
    assertThat(
      result.getDataEntryForKey(USER_TASK_2).get().getValue(),
      is(calculateExpectedValueGivenDurations(setDurations))
    );

    assertThat(result.getProcessInstanceCount(), is(2L));
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getIsComplete(), is(true));
    assertThat(result.getData().size(), is(2));
    assertThat(result.getDataEntryForKey(USER_TASK_1).get().getValue(), is(calculateExpectedValueGivenDurations(10L)));
    assertThat(result.getDataEntryForKey(USER_TASK_2).get().getValue(), is(calculateExpectedValueGivenDurations(20L)));
  }

  @Test
  public void evaluateReportForMultipleEvents_resultLimitedByConfig() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessDurationReportMapResultDto resultDto = evaluateReport(reportData).getResult();

    // then
    assertThat(resultDto.getProcessInstanceCount(), is(2L));
    assertThat(resultDto.getData(), is(notNullValue()));
    assertThat(resultDto.getData().size(), is(1));
    assertThat(resultDto.getIsComplete(), is(false));
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<AggregationResultDto>> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(
      resultKeys,
      // expect ascending order
      contains(resultKeys.stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnResultLabelIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_LABEL, SortOrder.DESC));
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<AggregationResultDto>> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    final List<String> resultLabels = resultData.stream()
      .map(MapResultEntryDto::getLabel)
      .collect(Collectors.toList());
    assertThat(
      resultLabels,
      // expect ascending order
      contains(resultLabels.stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }

  private static Object[] aggregationTypes() {
    return AggregationType.values();
  }

  @Test
  @Parameters(method = "aggregationTypes")
  public void testCustomOrderOnResultValueIsApplied(final AggregationType aggregationType) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 100L);
    changeDuration(processInstanceDto2, USER_TASK_2, 2L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setAggregationType(aggregationType);
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<AggregationResultDto>> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    final List<Long> bucketValues = resultData.stream()
      .map(entry -> entry.getValue().getResultForGivenAggregationType(aggregationType))
      .collect(Collectors.toList());
    assertThat(
      bucketValues,
      contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(firstDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(latestDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    //then
    assertThat(result.getData().size(), is(2));
    assertThat(
      result.getDataEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurations(20L, 40L))
    );
    assertThat(result.getDataEntryForKey(USER_TASK_2).get().getValue(), is(calculateExpectedValueGivenDurations(40L)));
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(firstDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(latestDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    //then
    assertThat(result.getData().size(), is(1));
    assertThat(
      result.getDataEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurations(20L, 40L))
    );
  }

  @Test
  public void reportAcrossAllVersions() {
    //given
    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto processDefinition2 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition1.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, 40L);
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition2.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData = createReport(
      processDefinition1.getKey(), ReportConstants.ALL_VERSIONS
    );
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    //then
    assertThat(result.getData().size(), is(1));
    assertThat(
      result.getDataEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurations(20L, 40L))
    );
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition1.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, 40L);
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition1.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    final ProcessDefinitionEngineDto processDefinition2 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(processDefinition2.getId());
    finishAllUserTasks(processInstanceDto3);
    changeDuration(processInstanceDto3, 20L);
    final ProcessInstanceEngineDto processInstanceDto4 = engineRule.startProcessInstance(processDefinition2.getId());
    finishAllUserTasks(processInstanceDto4);
    changeDuration(processInstanceDto4, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData1 = createReport(processDefinition1);
    final ProcessDurationReportMapResultDto result1 = evaluateReport(reportData1).getResult();
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ProcessDurationReportMapResultDto result2 = evaluateReport(reportData2).getResult();

    // then
    assertThat(result1.getData().size(), is(1));
    assertThat(result1.getDataEntryForKey(USER_TASK_1).get().getValue(), is(calculateExpectedValueGivenDurations(40L)));

    assertThat(result2.getData().size(), is(1));
    assertThat(result2.getDataEntryForKey(USER_TASK_1).get().getValue(), is(calculateExpectedValueGivenDurations(20L)));
  }

  @Test
  public void evaluateReportWithIrrationalNumberAsResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);
    changeDuration(processInstanceDto, 100L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);
    changeDuration(processInstanceDto, 300L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);
    changeDuration(processInstanceDto, 600L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(1));
    assertThat(
      result.getDataEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurations(100L, 300L, 600L))
    );
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(0));
  }

  @Test
  public void runningUserTasksAreNotConsidered() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    // finish first task
    engineRule.finishAllUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, USER_TASK_1, 100L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(1));
    assertThat(result.getDataEntryForKey(USER_TASK_1).get().getValue(), is(calculateExpectedValueGivenDurations(100L)));
  }

  @Test
  public void processDefinitionContainsMultiInstanceBody() {
    // given
    BpmnModelInstance processWithMultiInstanceUserTask = Bpmn
      // @formatter:off
        .createExecutableProcess("processWithMultiInstanceUserTask")
        .startEvent()
          .userTask(USER_TASK_1).multiInstance().cardinality("2").multiInstanceDone()
        .endEvent()
        .done();
    // @formatter:on

    final ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(
      processWithMultiInstanceUserTask
    );
    final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(1));
    assertThat(result.getDataEntryForKey(USER_TASK_1).get().getValue(), is(calculateExpectedValueGivenDurations(10L)));
  }

  @Test
  public void evaluateReportForMoreThenTenEvents() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();

    for (int i = 0; i < 11; i++) {
      final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
      engineRule.finishAllUserTasks(processInstanceDto.getId());
      changeDuration(processInstanceDto, 10L);
    }

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(1));
    assertThat(result.getDataEntryForKey(USER_TASK_1).get().getValue(), is(calculateExpectedValueGivenDurations(10L)));
  }

  @Test
  public void filterInReport() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    final OffsetDateTime processStartTime = engineRule.getHistoricProcessInstance(processInstanceDto.getId())
      .getStartTime();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, processStartTime.minusSeconds(1L)));
    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(0));

    // when
    reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(processStartTime, null));
    result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(result.getDataEntryForKey(USER_TASK_1).get().getValue(), is(calculateExpectedValueGivenDurations(10L)));
  }

  private List<ProcessFilterDto> createStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedStartDate().start(startDate).end(endDate).add().buildList();
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setEntity(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  protected abstract ProcessViewProperty getViewProperty();

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                         final String userTaskKey,
                                         final long duration);

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final long setDuration);

  protected abstract ProcessReportDataDto createReport(final String processDefinitionKey, final String version);

  private ProcessReportDataDto createReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private AggregationResultDto calculateExpectedValueGivenDurations(final Long... setDuration) {
    final DescriptiveStatistics statistics = new DescriptiveStatistics();
    Stream.of(setDuration).map(Long::doubleValue).forEach(statistics::addValue);

    return new AggregationResultDto(
      Math.round(statistics.getMin()),
      Math.round(statistics.getMax()),
      Math.round(statistics.getMean()),
      Math.round(statistics.getPercentile(50.0D))
    );
  }

  private void finishAllUserTasks(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish first task
    engineRule.finishAllUserTasks(processInstanceDto1.getId());
    // finish second task
    engineRule.finishAllUserTasks(processInstanceDto1.getId());
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .userTask(USER_TASK_2)
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluateReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto>>() {});
      // @formatter:on
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

}
