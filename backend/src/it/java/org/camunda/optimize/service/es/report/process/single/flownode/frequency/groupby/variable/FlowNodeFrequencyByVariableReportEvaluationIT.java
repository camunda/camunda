/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.variable;

import com.google.common.collect.ImmutableList;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

public class FlowNodeFrequencyByVariableReportEvaluationIT extends AbstractProcessDefinitionIT {

  @Test
  public void simpleReportEvaluation_stringVariable() {
    // given
    final ProcessInstanceEngineDto processInstanceDto =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("stringVar", "aStringValue"));
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "stringVar",
      VariableType.STRING
    );
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName()).isEqualTo("stringVar");
    assertThat(variableGroupByDto.getValue().getType()).isEqualTo(VariableType.STRING);

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "aStringValue"))
      .isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(4.);
  }

  @Test
  public void simpleReportEvaluation_numberVariable() {
    // given
    final ProcessInstanceEngineDto processInstanceDto =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("doubleVar", 1.0));
    importAllEngineEntitiesFromScratch();

    // when`
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "doubleVar",
      VariableType.DOUBLE
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "1.00"))
      .isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(4.);
  }

  @Test
  public void simpleReportEvaluation_numberVariable_customBuckets() {
    // given
    final String varName = "doubleVar";
    Map<String, Object> variables = new HashMap<>();
    variables.put(varName, 100.0);
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartTwoServiceTaskProcessWithVariables(variables);

    variables.put(varName, 200.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    variables.put(varName, 300.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "doubleVar",
      VariableType.DOUBLE
    );
    reportData.getConfiguration().getCustomBucket().setActive(true);
    reportData.getConfiguration().getCustomBucket().setBaseline(10.0);
    reportData.getConfiguration().getCustomBucket().setBucketSize(100.0);
    final List<MapResultEntryDto> resultData = reportClient.evaluateMapReport(reportData)
      .getResult()
      .getFirstMeasureData();

    // then
    assertThat(resultData).isNotNull().hasSize(3);
    assertThat(resultData)
      .extracting(MapResultEntryDto::getKey)
      .containsExactly("10.00", "110.00", "210.00");
    assertThat(resultData.get(0).getValue()).isEqualTo(4L);
    assertThat(resultData.get(1).getValue()).isEqualTo(4L);
    assertThat(resultData.get(2).getValue()).isEqualTo(4L);
  }

  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void simpleReportEvaluation_dateVariable_staticUnits(final AggregateByDateUnit unit) {
    // given
    final int numberOfInstances = 3;
    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    OffsetDateTime dateVarValue = OffsetDateTime.parse("2020-06-15T00:00:00+01:00");

    final ProcessInstanceEngineDto processInstanceDto =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("dateVar", dateVarValue));

    for (int i = 1; i < numberOfInstances; i++) {
      engineIntegrationExtension.startProcessInstance(
        processInstanceDto.getDefinitionId(),
        Collections.singletonMap("dateVar", dateVarValue.plus(i, chronoUnit))
      );
    }

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "dateVar",
      VariableType.DATE
    );
    reportData.getConfiguration().setGroupByDateVariableUnit(unit);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(numberOfInstances);
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(numberOfInstances);

    for (int i = 0; i < numberOfInstances; i++) {
      final String expectedBucketKey = embeddedOptimizeExtension.formatToHistogramBucketKey(
        dateVarValue.plus(chronoUnit.getDuration().multipliedBy(i)),
        chronoUnit
      );
      assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), expectedBucketKey))
        .isPresent()
        .get()
        .extracting(MapResultEntryDto::getValue)
        .isEqualTo(4.);
    }
  }

  @Test
  public void simpleReportEvaluation_dateVariable_automaticUnit() {
    // given
    final int numberOfInstances = 3;
    OffsetDateTime dateVarValue = OffsetDateTime.parse("2020-06-15T00:00:00+01:00");

    final ProcessInstanceEngineDto processInstanceDto =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("dateVar", dateVarValue));

    for (int i = 1; i < numberOfInstances; i++) {
      engineIntegrationExtension.startProcessInstance(
        processInstanceDto.getDefinitionId(),
        Collections.singletonMap("dateVar", dateVarValue.plusMinutes(i))
      );
    }

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "dateVar",
      VariableType.DATE
    );
    reportData.getConfiguration().setGroupByDateVariableUnit(AggregateByDateUnit.AUTOMATIC);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(numberOfInstances);
    assertThat(result.getFirstMeasureData()).isNotNull()
      .hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);

    // the bucket span covers the earliest and the latest date variable value
    final DateTimeFormatter formatter = embeddedOptimizeExtension.getDateTimeFormatter();
    final OffsetDateTime startOfFirstBucket = OffsetDateTime.from(formatter.parse(result.getFirstMeasureData()
                                                                                    .get(0)
                                                                                    .getKey()));
    final OffsetDateTime startOfLastBucket = OffsetDateTime
      .from(formatter.parse(result.getFirstMeasureData()
                              .get(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1)
                              .getKey()));
    final OffsetDateTime firstTruncatedDateVariableValue =
      dateVarValue.plusMinutes(numberOfInstances).truncatedTo(ChronoUnit.MILLIS);
    final OffsetDateTime lastTruncatedDateVariableValue = dateVarValue.truncatedTo(ChronoUnit.MILLIS);

    assertThat(startOfFirstBucket).isBeforeOrEqualTo(firstTruncatedDateVariableValue);
    assertThat(startOfLastBucket).isAfterOrEqualTo(lastTruncatedDateVariableValue);
    assertThat(result.getFirstMeasureData().stream().mapToDouble(MapResultEntryDto::getValue).sum())
      .isEqualTo(4.0 * numberOfInstances); // each instance went through 4 flownodes
  }

  @Test
  public void missingVariablesAggregationWorksForUndefinedAndNullVariables() {
    // given
    // 1 process instance with 'testVar'
    ProcessInstanceEngineDto processInstanceDto =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("testVar", "withValue"));

    // 4 process instances without 'testVar'
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    engineIntegrationExtension.startProcessInstance(
      processInstanceDto.getDefinitionId(),
      Collections.singletonMap("testVar", null)
    );
    engineIntegrationExtension.startProcessInstance(
      processInstanceDto.getDefinitionId(),
      Collections.singletonMap("testVar", new EngineVariableValue(null, "String"))
    );
    engineIntegrationExtension.startProcessInstance(
      processInstanceDto.getDefinitionId(),
      Collections.singletonMap("differentStringValue", "test")
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "testVar",
      VariableType.STRING
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then the instance withValue has passed through 4 flownodes and the 4 instances without the variable
    // have passed through 16 flownodes (4 flownodes each)
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "withValue")).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(4.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "missing")).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(16.);
  }

  @Test
  public void resultIncludesDataFromAllVersions() {
    // given
    final Map<String, Object> variables = Collections.singletonMap(
      "stringVar",
      "aStringValue"
    );
    deployAndStartSimpleProcessWithVariables(variables);
    final ProcessInstanceEngineDto latestProcess = deployAndStartTwoServiceTaskProcessWithVariables(variables);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      latestProcess.getProcessDefinitionKey(),
      ALL_VERSIONS,
      "stringVar",
      VariableType.STRING
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then the result counts 2 flownodes from first definition plus 4 flownodes of the latest definition
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "aStringValue")).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(6.);
  }

  @Test
  public void resultIncludesDataFromMultipleVersions() {
    // given
    final Map<String, Object> variables = Collections.singletonMap(
      "stringVar",
      "aStringValue"
    );
    final ProcessInstanceEngineDto firstProcessInst = deployAndStartSimpleProcessWithVariables(variables);
    deployAndStartSimpleProcessWithVariables(variables);
    final ProcessInstanceEngineDto latestProcessInst = deployAndStartTwoServiceTaskProcessWithVariables(variables);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      latestProcessInst.getProcessDefinitionKey(),
      ImmutableList.of(firstProcessInst.getProcessDefinitionVersion(), latestProcessInst.getProcessDefinitionVersion()),
      "stringVar",
      VariableType.STRING
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then the result counts 2 flownodes of the first decfinition plus 4 flownodes of the latest definition
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "aStringValue")).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(6.);
  }

  @Test
  @SneakyThrows
  public void worksWithMIProcess() {
    // given
    final String subProcessKey = "testProcess";
    final String testMIProcess = "testMIProcess";

    final BpmnModelInstance subProcess = BpmnModels.getSingleServiceTaskProcess(subProcessKey);
    engineIntegrationExtension.deployProcessAndGetId(subProcess);

    final BpmnModelInstance model = BpmnModels.getMultiInstanceProcess(testMIProcess, subProcessKey);
    engineIntegrationExtension.deployAndStartProcessWithVariables(
      model,
      Collections.singletonMap("stringVar", "aStringValue")
    );

    engineIntegrationExtension.waitForAllProcessesToFinish();
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
      createReport(testMIProcess, "1", "stringVar", VariableType.STRING);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then the result counts 7 flownodes
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "aStringValue")).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(7.);
  }

  private static Stream<Arguments> flowNodeDurationFiltersAndExpectedValues() {
    return Stream.of(
      Arguments.of(FilterApplicationLevel.INSTANCE, 3.),
      Arguments.of(FilterApplicationLevel.VIEW, 1.)
    );
  }

  @ParameterizedTest
  @MethodSource("flowNodeDurationFiltersAndExpectedValues")
  public void worksWithFlowNodeDurationFilter(final FilterApplicationLevel filterApplicationLevel,
                                              final Double expectedCount) {
    // given
    final ProcessInstanceEngineDto firstInstance =
      deployAndStartSimpleUserTaskProcess(Collections.singletonMap("doubleVar", 1.0));
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineDatabaseExtension.changeFlowNodeTotalDuration(firstInstance.getId(), USER_TASK_1, 10000);
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension.startProcessInstance(
      firstInstance.getDefinitionId(), Collections.singletonMap("doubleVar", 2.0));
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineDatabaseExtension.changeFlowNodeTotalDuration(secondInstance.getId(), USER_TASK_1, 20000);
    importAllEngineEntitiesFromScratch();

    // when`
    final ProcessReportDataDto reportData = createReport(
      firstInstance.getProcessDefinitionKey(),
      firstInstance.getProcessDefinitionVersion(),
      "doubleVar",
      VariableType.DOUBLE
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .flowNodeDuration()
        .flowNode(USER_TASK_1, durationFilterData(DurationUnit.SECONDS, 15L, LESS_THAN))
        .filterLevel(filterApplicationLevel)
        .add()
        .buildList());
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "1.00"))
      .isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(expectedCount);
  }

  private static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{DEFAULT_USERNAME},
        1.
      ),
      Arguments.of(
        IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER, null},
        3.
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_USER},
        2.
      ),
      Arguments.of(
        NOT_IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER},
        1.
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelAssigneeFilterScenarios")
  public void viewLevelAssigneeFilterOnlyIncludesFlowNodesMatchingFilter(final MembershipFilterOperator filterOperator,
                                                                         final String[] filterValues,
                                                                         final Double expectedResult) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USER_FIRST_NAME, SECOND_USER_LAST_NAME);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
    final ProcessInstanceEngineDto processInstance = deployAndStartThreeUserTasksDefinition(Collections.singletonMap(
      "doubleVar",
      1.0
    ));
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstance.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstance.getId()
    );
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstance.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      "doubleVar",
      VariableType.DOUBLE
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .assignee()
        .ids(filterValues)
        .operator(filterOperator)
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList());
    final ReportResultResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "1.00"))
      .isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(expectedResult);
  }

  private static Stream<Arguments> viewLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID},
        1.
      ),
      Arguments.of(
        IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID, null},
        3.
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        2.
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        1.
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelCandidateGroupFilterScenarios")
  public void viewLevelCandidateGroupFilterOnlyIncludesFlowNodesMatchingFilter(final MembershipFilterOperator filterOperator,
                                                                               final String[] filterValues,
                                                                               final Double expectedResult) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME);
    final ProcessInstanceEngineDto processInstance = deployAndStartThreeUserTasksDefinition(Collections.singletonMap(
      "doubleVar",
      1.0
    ));
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      "doubleVar",
      VariableType.DOUBLE
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .candidateGroups()
        .ids(filterValues)
        .operator(filterOperator)
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList());
    final ReportResultResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "1.00"))
      .isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(expectedResult);
  }

  private ProcessReportDataDto createReport(final String processDefinitionKey,
                                            final String processDefinitionVersion,
                                            final String variableName,
                                            final VariableType variableType) {
    return createReport(
      processDefinitionKey,
      Collections.singletonList(processDefinitionVersion),
      variableName,
      variableType
    );
  }

  private ProcessReportDataDto createReport(final String processDefinitionKey,
                                            final List<String> processDefinitionVersions,
                                            final String variableName,
                                            final VariableType variableType) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(processDefinitionVersions)
      .setTenantIds(Collections.singletonList(null))
      .setVariableName(variableName)
      .setVariableType(variableType)
      .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_VARIABLE)
      .build();
  }
}
