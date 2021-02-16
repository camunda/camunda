/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.duration.groupby.variable;

import com.google.common.collect.ImmutableList;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
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
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

public class FlowNodeDurationByVariableReportEvaluationIT extends AbstractProcessDefinitionIT {

  @Test
  public void simpleReportEvaluation_stringVariable() {
    // given
    final ProcessInstanceEngineDto processInstanceDto =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("stringVar", "aStringValue"));
    changeActivityDuration(processInstanceDto, 10.);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "stringVar",
      VariableType.STRING
    );
    final AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName()).isEqualTo("stringVar");
    assertThat(variableGroupByDto.getValue().getType()).isEqualTo(VariableType.STRING);

    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(result.getEntryForKey("aStringValue"))
      .isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(10.);
  }

  @Test
  public void simpleReportEvaluation_numberVariable() {
    // given
    final ProcessInstanceEngineDto processInstanceDto =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("doubleVar", 1.0));
    changeActivityDuration(processInstanceDto, 10.);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "doubleVar",
      VariableType.DOUBLE
    );
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(result.getEntryForKey("1.00"))
      .isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(10.);
  }

  @Test
  public void simpleReportEvaluation_numberVariable_customBuckets() {
    // given
    final String varName = "doubleVar";
    Map<String, Object> variables = new HashMap<>();
    variables.put(varName, 100.0);
    final ProcessInstanceEngineDto procInst1 = deployAndStartTwoServiceTaskProcessWithVariables(variables);
    changeActivityDuration(procInst1, 10.);

    variables.put(varName, 200.0);
    final ProcessInstanceEngineDto procInst2 = engineIntegrationExtension.startProcessInstance(
      procInst1.getDefinitionId(),
      variables
    );
    changeActivityDuration(procInst2, 10.);

    variables.put(varName, 300.0);
    final ProcessInstanceEngineDto procInst3 = engineIntegrationExtension.startProcessInstance(
      procInst1.getDefinitionId(),
      variables
    );
    changeActivityDuration(procInst3, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      procInst1.getProcessDefinitionKey(),
      procInst1.getProcessDefinitionVersion(),
      "doubleVar",
      VariableType.DOUBLE
    );
    reportData.getConfiguration().getCustomBucket().setActive(true);
    reportData.getConfiguration().getCustomBucket().setBaseline(10.0);
    reportData.getConfiguration().getCustomBucket().setBucketSize(100.0);
    final List<MapResultEntryDto> resultData = reportClient.evaluateMapReport(reportData).getResult().getFirstMeasureData();

    // then
    assertThat(resultData).isNotNull().hasSize(3);
    assertThat(resultData)
      .extracting(MapResultEntryDto::getKey)
      .containsExactly("10.00", "110.00", "210.00");
    assertThat(resultData.get(0).getValue()).isEqualTo(10.);
    assertThat(resultData.get(1).getValue()).isEqualTo(10.);
    assertThat(resultData.get(2).getValue()).isEqualTo(10.);
  }

  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void simpleReportEvaluation_dateVariable_staticUnits(final AggregateByDateUnit unit) {
    // given
    final int numberOfInstances = 3;
    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    OffsetDateTime dateVarValue = OffsetDateTime.parse("2020-06-15T00:00:00+01:00");

    ProcessInstanceEngineDto processInstanceDto =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("dateVar", dateVarValue));
    changeActivityDuration(processInstanceDto, 10.);

    for (int i = 1; i < numberOfInstances; i++) {
      final ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(
        processInstanceDto.getDefinitionId(),
        Collections.singletonMap("dateVar", dateVarValue.plus(i, chronoUnit))
      );
      changeActivityDuration(instance, 10.);
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
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(numberOfInstances);
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(numberOfInstances);

    for (int i = 0; i < numberOfInstances; i++) {
      final String expectedBucketKey = embeddedOptimizeExtension.formatToHistogramBucketKey(
        dateVarValue.plus(chronoUnit.getDuration().multipliedBy(i)),
        chronoUnit
      );
      assertThat(result.getEntryForKey(expectedBucketKey))
        .isPresent()
        .get()
        .extracting(MapResultEntryDto::getValue)
        .isEqualTo(10.);
    }
  }

  @Test
  public void simpleReportEvaluation_dateVariable_automaticUnit() {
    // given
    final int numberOfInstances = 3;
    OffsetDateTime dateVarValue = OffsetDateTime.parse("2020-06-15T00:00:00+01:00");

    ProcessInstanceEngineDto processInstanceDto =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("dateVar", dateVarValue));
    changeActivityDuration(processInstanceDto, 10.);

    for (int i = 1; i < numberOfInstances; i++) {
      final ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(
        processInstanceDto.getDefinitionId(),
        Collections.singletonMap("dateVar", dateVarValue.plusMinutes(i))
      );
      changeActivityDuration(instance, 10.);
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
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(numberOfInstances);
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);

    // the bucket span covers the earliest and the latest date variable value
    final DateTimeFormatter formatter = embeddedOptimizeExtension.getDateTimeFormatter();
    final OffsetDateTime startOfFirstBucket = OffsetDateTime.from(formatter.parse(result.getFirstMeasureData().get(0).getKey()));
    final OffsetDateTime startOfLastBucket = OffsetDateTime
      .from(formatter.parse(result.getFirstMeasureData().get(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1).getKey()));
    final OffsetDateTime firstTruncatedDateVariableValue =
      dateVarValue.plusMinutes(numberOfInstances).truncatedTo(ChronoUnit.MILLIS);
    final OffsetDateTime lastTruncatedDateVariableValue = dateVarValue.truncatedTo(ChronoUnit.MILLIS);

    assertThat(startOfFirstBucket).isBeforeOrEqualTo(firstTruncatedDateVariableValue);
    assertThat(startOfLastBucket).isAfterOrEqualTo(lastTruncatedDateVariableValue);
    assertThat(result.getFirstMeasureData().stream()
                 .filter(entry -> entry.getValue() != null)
                 .mapToDouble(MapResultEntryDto::getValue)
                 .sum())
      .isEqualTo(10. * numberOfInstances); // each instance has a duration of 10
  }

  @Test
  public void missingVariablesAggregationWorksForUndefinedAndNullVariables() {
    // given
    // 1 process instance with 'testVar'
    final ProcessInstanceEngineDto procInst1 =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("testVar", "withValue"));
    changeActivityDuration(procInst1, 10.);

    // 4 process instances without 'testVar'
    final ProcessInstanceEngineDto procInst2 =
      engineIntegrationExtension.startProcessInstance(procInst1.getDefinitionId());
    changeActivityDuration(procInst2, 20.);

    final ProcessInstanceEngineDto procInst3 = engineIntegrationExtension.startProcessInstance(
      procInst1.getDefinitionId(),
      Collections.singletonMap("testVar", null)
    );
    changeActivityDuration(procInst3, 20.);

    final ProcessInstanceEngineDto procInst4 = engineIntegrationExtension.startProcessInstance(
      procInst1.getDefinitionId(),
      Collections.singletonMap("testVar", new EngineVariableValue(null, "String"))
    );
    changeActivityDuration(procInst4, 20.);

    final ProcessInstanceEngineDto procInst5 = engineIntegrationExtension.startProcessInstance(
      procInst1.getDefinitionId(),
      Collections.singletonMap("differentStringValue", "test")
    );
    changeActivityDuration(procInst5, 20.);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      procInst1.getProcessDefinitionKey(),
      procInst1.getProcessDefinitionVersion(),
      "testVar",
      VariableType.STRING
    );
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then the instance withValue has a duration of 10 and the 4 missing instances each have a duration of 20
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(2);
    assertThat(result.getEntryForKey("withValue")).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(10.);
    assertThat(result.getEntryForKey("missing")).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(20.);
  }

  @Test
  public void resultIncludesDataFromAllVersions() {
    // given
    final Map<String, Object> variables = Collections.singletonMap(
      "stringVar",
      "aStringValue"
    );
    final ProcessInstanceEngineDto firstProcess = deployAndStartSimpleProcessWithVariables(variables);
    changeActivityDuration(firstProcess, 10.);
    final ProcessInstanceEngineDto latestProcess = deployAndStartTwoServiceTaskProcessWithVariables(variables);
    changeActivityDuration(latestProcess, 100.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      latestProcess.getProcessDefinitionKey(),
      ALL_VERSIONS,
      "stringVar",
      VariableType.STRING
    );
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then the result takes into account both processes (average duration both processes = 70)
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(result.getEntryForKey("aStringValue")).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(70.);
  }

  @Test
  public void resultIncludesDataFromMultipleVersions() {
    // given
    final Map<String, Object> variables = Collections.singletonMap(
      "stringVar",
      "aStringValue"
    );
    final ProcessInstanceEngineDto firstProcess = deployAndStartSimpleProcessWithVariables(variables);
    changeActivityDuration(firstProcess, 10.);
    final ProcessInstanceEngineDto secondProcess = deployAndStartSimpleProcessWithVariables(variables);
    changeActivityDuration(secondProcess, 10000.);
    final ProcessInstanceEngineDto latestProcess = deployAndStartTwoServiceTaskProcessWithVariables(variables);
    changeActivityDuration(latestProcess, 100.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      latestProcess.getProcessDefinitionKey(),
      ImmutableList.of(firstProcess.getProcessDefinitionVersion(), latestProcess.getProcessDefinitionVersion()),
      "stringVar",
      VariableType.STRING
    );
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then the result takes into account first and last processes (average duration both processes = 70)
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(result.getEntryForKey("aStringValue")).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(70.);
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
    final ProcessInstanceEngineDto instance = engineIntegrationExtension.deployAndStartProcessWithVariables(
      model,
      Collections.singletonMap("stringVar", "aStringValue")
    );
    engineIntegrationExtension.waitForAllProcessesToFinish();
    changeActivityDuration(instance, 10.);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
      createReport(testMIProcess, "1", "stringVar", VariableType.STRING);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then the result counts 7 flownodes
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(result.getEntryForKey("aStringValue")).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(10.);
  }

  private static Stream<Arguments> flowNodeDurationFiltersAndExpectedValues() {
    return Stream.of(
      Arguments.of(FilterApplicationLevel.INSTANCE, 2000.),
      Arguments.of(FilterApplicationLevel.VIEW, 1000.)
    );
  }

  @ParameterizedTest
  @MethodSource("flowNodeDurationFiltersAndExpectedValues")
  public void worksWithFlowNodeDurationFilter(final FilterApplicationLevel filterApplicationLevel,
                                              final Double expectedResult) {
    // given
    final ProcessInstanceEngineDto firstInstance =
      deployAndStartSimpleUserTaskProcess(Collections.singletonMap("doubleVar", 1.0));
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineDatabaseExtension.changeActivityDuration(firstInstance.getId(), START_EVENT, 2000);
    engineDatabaseExtension.changeActivityDuration(firstInstance.getId(), USER_TASK_1, 1000);
    engineDatabaseExtension.changeActivityDuration(firstInstance.getId(), END_EVENT, 3000);
    // We change the duration of only the user task of the second instance so it misses the filter
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension.startProcessInstance(
      firstInstance.getDefinitionId(), Collections.singletonMap("doubleVar", 2.0));
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineDatabaseExtension.changeActivityDuration(secondInstance.getId(), USER_TASK_1, 10000);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      firstInstance.getProcessDefinitionKey(),
      firstInstance.getProcessDefinitionVersion(),
      "doubleVar",
      VariableType.DOUBLE
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .flowNodeDuration()
        .flowNode(USER_TASK_1, durationFilterData(DurationFilterUnit.SECONDS, 5L, LESS_THAN))
        .filterLevel(filterApplicationLevel)
        .add()
        .buildList());
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(result.getEntryForKey("1.00"))
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
      .setReportDataType(ProcessReportDataType.FLOW_NODE_DURATION_GROUP_BY_VARIABLE)
      .build();
  }
}
