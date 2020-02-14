/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.user_task.duration.groupby.user_task.distributed_by.none;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.Data;
import org.apache.http.HttpStatus;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;

public abstract class AbstractUserTaskDurationByUserTaskReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String PROCESS_DEFINITION_KEY = "123";
  static final String USER_TASK_1 = "userTask1";
  static final String USER_TASK_2 = "userTask2";
  private final List<AggregationType> aggregationTypes = Arrays.asList(AggregationType.values());

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);

    final long setDuration = 20L;
    changeDuration(processInstanceDto, setDuration);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
      evaluateMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processDefinition.getVersionAsString()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.USER_TASK));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTime(), is(getUserTaskDurationTime()));

    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
    assertThat(getExecutedFlowNodeCount(result), is(2L));
    assertThat(
      result.getEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(setDuration))
    );
    assertThat(
      result.getEntryForKey(USER_TASK_2).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(setDuration))
    );

    assertThat(result.getInstanceCount(), is(1L));

  }

  @Test
  public void reportEvaluationForSeveralProcesses() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    final Long[] setDurations = new Long[]{10L, 30L};
    changeDuration(processInstanceDto1, setDurations[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, setDurations[1]);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(2));
    assertThat(getExecutedFlowNodeCount(result), is(2L));
    assertThat(
      result.getEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(setDurations))
    );
    assertThat(
      result.getEntryForKey(USER_TASK_2).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(setDurations))
    );

    assertThat(result.getInstanceCount(), is(2L));
  }

  @Test
  public void reportEvaluationForSeveralProcessesWithAllAggregationTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    final Long[] setDurations = new Long[]{10L, 30L};
    changeDuration(processInstanceDto1, setDurations[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, setDurations[1]);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final Map<AggregationType, ReportMapResultDto> results =
      evaluateMapReportForAllAggTypes(reportData);

    // then
    assertDurationMapReportResults(results, ImmutableMap.of(USER_TASK_1, setDurations, USER_TASK_2, setDurations));
    assertThat(results.get(MIN).getInstanceCount(), is(2L));
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getIsComplete(), is(true));
    assertThat(result.getData().size(), is(2));
    assertThat(getExecutedFlowNodeCount(result), is(2L));
    assertThat(
      result.getEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(10L))
    );
    assertThat(
      result.getEntryForKey(USER_TASK_2).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(20L))
    );
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final Map<AggregationType, ReportMapResultDto> results =
      evaluateMapReportForAllAggTypes(reportData);

    // then
    assertDurationMapReportResults(
      results,
      ImmutableMap.of(USER_TASK_1, new Long[]{10L}, USER_TASK_2, new Long[]{20L})
    );
    assertThat(results.get(MIN).getIsComplete(), is(true));
  }

  @Test
  public void evaluateReportForMultipleEvents_resultLimitedByConfig() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportMapResultDto resultDto = evaluateMapReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount(), is(2L));
    assertThat(resultDto.getData(), is(notNullValue()));
    assertThat(resultDto.getData().size(), is(1));
    assertThat(getExecutedFlowNodeCount(resultDto), is(1L));
    assertThat(resultDto.getIsComplete(), is(false));
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    aggregationTypes.forEach((AggregationType aggType) -> {
      // when
      reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));
      reportData.getConfiguration().setAggregationType(aggType);
      final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

      // then
      final List<MapResultEntryDto> resultData = result.getData();
      assertThat(resultData.size(), is(2));
      assertThat(getExecutedFlowNodeCount(result), is(2L));
      final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
      assertThat(
        resultKeys,
        // expect ascending order
        contains(resultKeys.stream().sorted(Comparator.reverseOrder()).toArray())
      );
    });
  }

  @Test
  public void testCustomOrderOnResultLabelIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_LABEL, SortOrder.DESC));
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    assertThat(getExecutedFlowNodeCount(result), is(2L));
    final List<String> resultLabels = resultData.stream()
      .map(MapResultEntryDto::getLabel)
      .collect(Collectors.toList());
    assertThat(
      resultLabels,
      // expect ascending order
      contains(resultLabels.stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }


  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 100L);
    changeDuration(processInstanceDto2, USER_TASK_2, 2L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    aggregationTypes.forEach((AggregationType aggType) -> {
      // when
      final ProcessReportDataDto reportData = createReport(processDefinition);
      reportData.getConfiguration().setAggregationType(aggType);
      reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
      final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

      // then
      assertThat(result.getData().size(), is(2));
      assertThat(getExecutedFlowNodeCount(result), is(2L));
      assertCorrectValueOrdering(result);
    });
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      firstDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    //then
    assertThat(result.getData().size(), is(2));
    assertThat(getExecutedFlowNodeCount(result), is(2L));
    assertThat(
      result.getEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(20L, 40L))
    );
    assertThat(
      result.getEntryForKey(USER_TASK_2).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(40L))
    );

  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(3));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      firstDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData =
      createReport(
        latestDefinition.getKey(),
        ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
      );
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    //then
    assertThat(result.getData().size(), is(2));
    assertThat(getExecutedFlowNodeCount(result), is(2L));
    assertThat(
      result.getEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(20L, 40L))
    );
    assertThat(
      result.getEntryForKey(USER_TASK_2).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(40L))
    );

  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      firstDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    //then
    assertThat(result.getData().size(), is(1));
    assertThat(getExecutedFlowNodeCount(result), is(1L));
    assertThat(
      result.getEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(20L, 40L))
    );
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(3));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      firstDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData =
      createReport(
        latestDefinition.getKey(),
        ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
      );
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    //then
    assertThat(result.getData().size(), is(1));
    assertThat(getExecutedFlowNodeCount(result), is(1L));
    assertThat(
      result.getEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(20L, 40L))
    );
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition1.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, 40L);
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition1.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    final ProcessDefinitionEngineDto processDefinition2 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinition2.getId());
    finishAllUserTasks(processInstanceDto3);
    changeDuration(processInstanceDto3, 20L);
    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processDefinition2.getId());
    finishAllUserTasks(processInstanceDto4);
    changeDuration(processInstanceDto4, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData1 = createReport(processDefinition1);
    final ReportMapResultDto result1 = evaluateMapReport(reportData1).getResult();
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ReportMapResultDto result2 = evaluateMapReport(reportData2).getResult();

    // then
    assertThat(result1.getData().size(), is(1));
    assertThat(getExecutedFlowNodeCount(result1), is(1L));
    assertThat(
      result1.getEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(40L))
    );

    assertThat(result2.getData().size(), is(1));
    assertThat(getExecutedFlowNodeCount(result2), is(1L));
    assertThat(
      result2.getEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(20L))
    );
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantUserTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ReportConstants.ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), CoreMatchers.is((long) selectedTenants.size()));
  }

  @Test
  public void evaluateReportWithIrrationalNumberAsResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);
    changeDuration(processInstanceDto, 100L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);
    changeDuration(processInstanceDto, 300L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);
    changeDuration(processInstanceDto, 600L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final Map<AggregationType, ReportMapResultDto> results =
      evaluateMapReportForAllAggTypes(reportData);

    // then
    assertDurationMapReportResults(results, ImmutableMap.of(USER_TASK_1, new Long[]{100L, 300L, 600L}));
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(0));
  }

  @Data
  static class ExecutionStateTestValues {
    FlowNodeExecutionState executionState;
    Map<String, Long> expectedIdleDurationValues;
    Map<String, Long> expectedWorkDurationValues;
    Map<String, Long> expectedTotalDurationValues;
  }

  private static Map<String, Long> getExpectedResultsMap(Long userTask1Results, Long userTask2Results) {
    Map<String, Long> result = new HashMap<>();
    result.put(USER_TASK_1, userTask1Results);
    result.put(USER_TASK_2, userTask2Results);
    return result;
  }

  protected static Stream<ExecutionStateTestValues> getExecutionStateExpectedValues() {
    ExecutionStateTestValues runningStateValues =
      new ExecutionStateTestValues();
    runningStateValues.executionState = FlowNodeExecutionState.RUNNING;
    runningStateValues.expectedIdleDurationValues = getExpectedResultsMap(200L, 500L);
    runningStateValues.expectedWorkDurationValues = getExpectedResultsMap(500L, null);
    runningStateValues.expectedTotalDurationValues = getExpectedResultsMap(700L, 500L);


    ExecutionStateTestValues completedStateValues = new ExecutionStateTestValues();
    completedStateValues.executionState = FlowNodeExecutionState.COMPLETED;
    completedStateValues.expectedIdleDurationValues = getExpectedResultsMap(100L, null);
    completedStateValues.expectedWorkDurationValues = getExpectedResultsMap(100L, null);
    completedStateValues.expectedTotalDurationValues = getExpectedResultsMap(100L, null);


    ExecutionStateTestValues allStateValues = new ExecutionStateTestValues();
    allStateValues.executionState = FlowNodeExecutionState.ALL;
    allStateValues.expectedIdleDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100L, 200L),
      500L
    );
    allStateValues.expectedWorkDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100L, 500L),
      null
    );
    allStateValues.expectedTotalDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100L, 700L),
      500L
    );

    return Stream.of(runningStateValues, completedStateValues, allStateValues);
  }

  @ParameterizedTest
  @MethodSource("getExecutionStateExpectedValues")
  public void evaluateReportWithExecutionState(ExecutionStateTestValues executionStateTestValues) {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish first running task, second now runs but unclaimed
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, USER_TASK_1, 100L);
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK_2, 500L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // claim first running task
    engineIntegrationExtension.claimAllRunningUserTasks(processInstanceDto2.getId());

    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_1, 700L);
    changeUserTaskClaimDate(processInstanceDto2, now, USER_TASK_1, 500L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setFlowNodeExecutionState(executionStateTestValues.executionState);
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertEvaluateReportWithExecutionState(result, executionStateTestValues);
  }

  protected abstract void assertEvaluateReportWithExecutionState(ReportMapResultDto result,
                                                                 ExecutionStateTestValues expectedValues);

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

    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      processWithMultiInstanceUserTask
    );
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(1));
    assertThat(getExecutedFlowNodeCount(result), is(1L));
    assertThat(
      result.getEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(10L, 10L))
    );
  }

  @Test
  public void evaluateReportForMoreThanTenEvents() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();

    for (int i = 0; i < 11; i++) {
      final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
        processDefinition.getId());
      engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
      changeDuration(processInstanceDto, 10L);
    }

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(1));
    assertThat(getExecutedFlowNodeCount(result), is(1L));
    assertThat(
      result.getEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(10L))
    );
  }

  @Test
  public void filterInReport() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    final OffsetDateTime processStartTime = engineIntegrationExtension.getHistoricProcessInstance(processInstanceDto
                                                                                                        .getId())
      .getStartTime();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, processStartTime.minusSeconds(1L)));
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(getExecutedFlowNodeCount(result), is(0L));

    // when
    reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(processStartTime, null));
    result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(getExecutedFlowNodeCount(result), is(1L));
    assertThat(
      result.getEntryForKey(USER_TASK_1).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(10L))
    );
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
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  private void changeUserTaskStartDate(final ProcessInstanceEngineDto processInstanceDto,
                                       final OffsetDateTime now,
                                       final String userTaskId,
                                       final long offsetDuration) {
    try {
      engineDatabaseExtension.changeUserTaskStartDate(
        processInstanceDto.getId(),
        userTaskId,
        now.minus(offsetDuration, ChronoUnit.MILLIS)
      );
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  private void changeUserTaskClaimDate(final ProcessInstanceEngineDto processInstanceDto,
                                       final OffsetDateTime now,
                                       final String userTaskKey,
                                       final long offsetDuration) {

    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto ->
        {
          try {
            engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
              historicUserTaskInstanceDto.getId(),
              now.minus(offsetDuration, ChronoUnit.MILLIS)
            );
          } catch (SQLException e) {
            throw new OptimizeIntegrationTestException(e);
          }
        }
      );
  }

  protected abstract UserTaskDurationTime getUserTaskDurationTime();

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                         final String userTaskKey,
                                         final long duration);

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final long setDuration);

  protected abstract ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions);

  private ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return createReport(processDefinitionKey, newArrayList(version));
  }

  private ProcessReportDataDto createReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }


  private void finishAllUserTasks(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish first task
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    // finish second task
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
  }

  protected String deployAndStartMultiTenantUserTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {
        final ProcessDefinitionEngineDto processDefinitionEngineDto = deployOneUserTasksDefinition(processKey, tenant);
        engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
      });

    return processKey;
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition() {
    return deployOneUserTasksDefinition("aProcess", null);
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition(String key, String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(key)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .userTask(USER_TASK_2)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  long getExecutedFlowNodeCount(ReportMapResultDto resultList) {
    return resultList.getData()
      .stream()
      .map(MapResultEntryDto::getValue)
      .filter(Objects::nonNull)
      .count();
  }

  private void assertCorrectValueOrdering(ReportMapResultDto result) {
    List<MapResultEntryDto> resultData = result.getData();
    final List<Long> bucketValues = resultData.stream()
      .map(MapResultEntryDto::getValue)
      .collect(Collectors.toList());
    final List<Long> bucketValuesWithoutNullValue = bucketValues.stream()
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    assertThat(
      bucketValuesWithoutNullValue,
      contains(bucketValuesWithoutNullValue.stream().sorted(Comparator.naturalOrder()).toArray())
    );
    long notExecutedFlowNodes = resultData.size() - getExecutedFlowNodeCount(result);
    for (int i = resultData.size() - 1; i > getExecutedFlowNodeCount(result) - 1; i--) {
      assertThat(bucketValues.get(i), nullValue());
    }
  }

  private Map<AggregationType, ReportMapResultDto> evaluateMapReportForAllAggTypes(final ProcessReportDataDto reportData) {

    Map<AggregationType, ReportMapResultDto> resultsMap = new HashMap<>();
    aggregationTypes.forEach((AggregationType aggType) -> {
      reportData.getConfiguration().setAggregationType(aggType);
      final ReportMapResultDto result = evaluateMapReport(reportData).getResult();
      resultsMap.put(aggType, result);
    });
    return resultsMap;
  }

  private void assertDurationMapReportResults(Map<AggregationType, ReportMapResultDto> results,
                                              Map<String, Long[]> expectedUserTaskValues) {

    aggregationTypes.forEach((AggregationType aggType) -> {
      ReportMapResultDto result = results.get(aggType);
      assertThat(result.getData(), is(notNullValue()));

      expectedUserTaskValues.keySet().forEach((String userTaskKey) -> assertThat(
        result.getEntryForKey(userTaskKey).get().getValue(),
        is(calculateExpectedValueGivenDurations(expectedUserTaskValues.get(userTaskKey)).get(aggType))
      ));

    });
  }


}
