/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.variable;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.BpmnModels.CALL_ACTIVITY;
import static org.camunda.optimize.util.BpmnModels.MULTI_INSTANCE_END;
import static org.camunda.optimize.util.BpmnModels.MULTI_INSTANCE_START;
import static org.camunda.optimize.util.BpmnModels.PARALLEL_GATEWAY;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_1;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_2;

public class CountFlowNodeFrequencyByVariableByFlowNodeReportEvaluationIT extends AbstractProcessDefinitionIT {

  @Test
  public void simpleReportEvaluation_StringVariable() {
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
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);
    final ReportHyperMapResultDto result = evaluationResponse.getResult();
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();

    // then
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    assertThat(resultReportDataDto.getDistributedBy().getType()).isEqualTo(DistributedByType.FLOW_NODE);
    final VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName()).isEqualTo("stringVar");
    assertThat(variableGroupByDto.getValue().getType()).isEqualTo(VariableType.STRING);

    //@formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains("aStringValue")
        .distributedByContains(END_EVENT, 1., END_EVENT)
        .distributedByContains(SERVICE_TASK_ID_1, 1., SERVICE_TASK_ID_1)
        .distributedByContains(SERVICE_TASK_ID_2, 1., SERVICE_TASK_ID_2)
        .distributedByContains(START_EVENT, 1., START_EVENT)
      .doAssert(result);
    //@formatter:on
  }

  @Test
  public void simpleReportEvaluation_numberVariable() {
    // given
    final ProcessInstanceEngineDto processInstanceDto =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("doubleVar", 1.0));
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "doubleVar",
      VariableType.DOUBLE
    );
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    //@formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains("1.00")
        .distributedByContains(END_EVENT, 1., END_EVENT)
        .distributedByContains(SERVICE_TASK_ID_1, 1., SERVICE_TASK_ID_1)
        .distributedByContains(SERVICE_TASK_ID_2, 1., SERVICE_TASK_ID_2)
        .distributedByContains(START_EVENT, 1., START_EVENT)
      .doAssert(result);
    //@formatter:on
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
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    //@formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .groupByContains("10.00")
        .distributedByContains(END_EVENT, 1., END_EVENT)
        .distributedByContains(SERVICE_TASK_ID_1, 1., SERVICE_TASK_ID_1)
        .distributedByContains(SERVICE_TASK_ID_2, 1., SERVICE_TASK_ID_2)
        .distributedByContains(START_EVENT, 1., START_EVENT)
      .groupByContains("110.00")
        .distributedByContains(END_EVENT, 1., END_EVENT)
        .distributedByContains(SERVICE_TASK_ID_1, 1., SERVICE_TASK_ID_1)
        .distributedByContains(SERVICE_TASK_ID_2, 1., SERVICE_TASK_ID_2)
        .distributedByContains(START_EVENT, 1., START_EVENT)
      .groupByContains("210.00")
        .distributedByContains(END_EVENT, 1., END_EVENT)
        .distributedByContains(SERVICE_TASK_ID_1, 1., SERVICE_TASK_ID_1)
        .distributedByContains(SERVICE_TASK_ID_2, 1., SERVICE_TASK_ID_2)
        .distributedByContains(START_EVENT, 1., START_EVENT)
      .doAssert(result);
    //@formatter:on
  }

  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void simpleReportEvaluation_dateVariable_staticUnits(final AggregateByDateUnit unit) {
    // given
    final int numberOfInstances = 3;
    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    OffsetDateTime dateVarValue = OffsetDateTime.parse("2020-06-15T00:00:00+01:00");
    List<OffsetDateTime> dateVarValues = new ArrayList<>();
    dateVarValues.add(dateVarValue);

    final ProcessInstanceEngineDto processInstanceDto =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("dateVar", dateVarValue));

    IntStream.range(1, numberOfInstances).forEach(i -> {
      final OffsetDateTime nextDateVarValue = dateVarValue.plus(i, chronoUnit);
      dateVarValues.add(nextDateVarValue);
      engineIntegrationExtension.startProcessInstance(
        processInstanceDto.getDefinitionId(),
        Collections.singletonMap("dateVar", nextDateVarValue)
      );
    });
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "dateVar",
      VariableType.DATE
    );
    reportData.getConfiguration().setGroupByDateVariableUnit(unit);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    HyperMapAsserter asserter = HyperMapAsserter.asserter()
      .processInstanceCount(numberOfInstances)
      .processInstanceCountWithoutFilters(numberOfInstances);

    dateVarValues
      .forEach(date -> {
        final String expectedBucketKey = embeddedOptimizeExtension.formatToHistogramBucketKey(date, chronoUnit);
        final HyperMapAsserter.GroupByAdder groupByAdder = asserter.groupByContains(expectedBucketKey)
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(SERVICE_TASK_ID_1, 1., SERVICE_TASK_ID_1)
          .distributedByContains(SERVICE_TASK_ID_2, 1., SERVICE_TASK_ID_2)
          .distributedByContains(START_EVENT, 1., START_EVENT);
        groupByAdder.add();
      });
    asserter.doAssert(result);
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
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(numberOfInstances);
    assertThat(result.getData()).isNotNull().hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);

    // the bucket span covers the earliest and the latest date variable value
    final DateTimeFormatter formatter = embeddedOptimizeExtension.getDateTimeFormatter();
    final OffsetDateTime startOfFirstBucket = OffsetDateTime.from(formatter.parse(result.getData().get(0).getKey()));
    final OffsetDateTime startOfLastBucket = OffsetDateTime
      .from(formatter.parse(result.getData().get(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1).getKey()));
    final OffsetDateTime firstTruncatedDateVariableValue =
      dateVarValue.plusMinutes(numberOfInstances).truncatedTo(ChronoUnit.MILLIS);
    final OffsetDateTime lastTruncatedDateVariableValue = dateVarValue.truncatedTo(ChronoUnit.MILLIS);

    assertThat(startOfFirstBucket).isBeforeOrEqualTo(firstTruncatedDateVariableValue);
    assertThat(startOfLastBucket).isAfterOrEqualTo(lastTruncatedDateVariableValue);
    assertThat(result.getData()
                 .stream()
                 .flatMap(hyperEntry -> hyperEntry.getValue().stream())
                 .filter(mapEntry -> mapEntry.getValue() != null)
                 .mapToDouble(MapResultEntryDto::getValue)
                 .sum())
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
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    //@formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(5L)
      .processInstanceCountWithoutFilters(5L)
      .groupByContains("withValue")
        .distributedByContains(END_EVENT, 1., END_EVENT)
        .distributedByContains(SERVICE_TASK_ID_1, 1., SERVICE_TASK_ID_1)
        .distributedByContains(SERVICE_TASK_ID_2, 1., SERVICE_TASK_ID_2)
        .distributedByContains(START_EVENT, 1., START_EVENT)
      .groupByContains("missing")
        .distributedByContains(END_EVENT, 4., END_EVENT)
        .distributedByContains(SERVICE_TASK_ID_1, 4., SERVICE_TASK_ID_1)
        .distributedByContains(SERVICE_TASK_ID_2, 4., SERVICE_TASK_ID_2)
        .distributedByContains(START_EVENT, 4., START_EVENT)
      .doAssert(result);
    //@formatter:on
  }

  @Test
  public void resultContainsNonExecutedFlowNodes() {
    // given
    final ProcessInstanceEngineDto processInstanceDto =
      deployAndStartSimpleUserTaskProcess(Collections.singletonMap("stringVar", "aStringValue"));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "stringVar",
      VariableType.STRING
    );
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    //@formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains("aStringValue")
        .distributedByContains(END_EVENT, null, END_EVENT)
        .distributedByContains(START_EVENT, 1., START_EVENT)
        .distributedByContains(USER_TASK_1, 1., USER_TASK_1)
      .doAssert(result);
    //@formatter:on
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
    ProcessReportDataDto reportData = createReport(
      testMIProcess,
      "1",
      "stringVar",
      VariableType.STRING
    );
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then the result counts the multi instance process correctly
    //@formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains("aStringValue")
        .distributedByContains(CALL_ACTIVITY, 2., CALL_ACTIVITY)
        .distributedByContains(END_EVENT, 1., END_EVENT)
        .distributedByContains(MULTI_INSTANCE_END, 1., MULTI_INSTANCE_END)
        .distributedByContains(MULTI_INSTANCE_START, 1., MULTI_INSTANCE_START)
        .distributedByContains(PARALLEL_GATEWAY, 1., PARALLEL_GATEWAY)
      .doAssert(result);
    //@formatter:on
  }

  @Test
  public void allVersions_resultIncludesLatestNodesOnly_latestHasMoreNodes() {
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
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then the result includes all flownodes of the latest version with instance counts from all versions
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains("aStringValue")
        .distributedByContains(END_EVENT, 2., END_EVENT)
        .distributedByContains(SERVICE_TASK_ID_1, 1., SERVICE_TASK_ID_1)
        .distributedByContains(SERVICE_TASK_ID_2, 1., SERVICE_TASK_ID_2)
        .distributedByContains(START_EVENT, 2., START_EVENT)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void allVersions_resultIncludesLatestNodesOnly_latestHasFewerNodes() {
    // given
    final Map<String, Object> variables = Collections.singletonMap(
      "stringVar",
      "aStringValue"
    );
    deployAndStartTwoServiceTaskProcessWithVariables(variables);
    final ProcessInstanceEngineDto latestProcess = deployAndStartSimpleProcessWithVariables(variables);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      latestProcess.getProcessDefinitionKey(),
      ALL_VERSIONS,
      "stringVar",
      VariableType.STRING
    );
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then the result includes only the flownodes of the latest version with instance counts from all versions
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains("aStringValue")
        .distributedByContains(END_EVENT, 2., END_EVENT)
        .distributedByContains(START_EVENT, 2., START_EVENT)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void multipleVersions_resultIncludesLatestNodesOnly_latestHasMoreNodes() {
    // given
    final Map<String, Object> variables = Collections.singletonMap(
      "stringVar",
      "aStringValue"
    );
    deployAndStartSimpleProcessWithVariables(variables);
    final ProcessInstanceEngineDto secondProcess = deployAndStartTwoServiceTaskProcessWithVariables(variables);
    deployAndStartSimpleProcessWithVariables(variables);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      secondProcess.getProcessDefinitionKey(),
      Arrays.asList("1", "2"),
      "stringVar",
      VariableType.STRING
    );
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then the result includes all flownodes of the latest version specified in the report (2)
    // with instance counts from all specified versions (1 and 2)
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains("aStringValue")
        .distributedByContains(END_EVENT, 2., END_EVENT)
        .distributedByContains(SERVICE_TASK_ID_1, 1., SERVICE_TASK_ID_1)
        .distributedByContains(SERVICE_TASK_ID_2, 1., SERVICE_TASK_ID_2)
        .distributedByContains(START_EVENT, 2., START_EVENT)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void multipleVersions_resultIncludesLatestNodesOnly_latestHasFewerNodes() {
    // given
    final Map<String, Object> variables = Collections.singletonMap(
      "stringVar",
      "aStringValue"
    );
    deployAndStartSimpleProcessWithVariables(variables);
    final ProcessInstanceEngineDto secondProcess = deployAndStartSimpleProcessWithVariables(variables);
    deployAndStartTwoServiceTaskProcessWithVariables(variables);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      secondProcess.getProcessDefinitionKey(),
      Arrays.asList("1", "2"),
      "stringVar",
      VariableType.STRING
    );
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then the result includes all flownodes of the latest version specified in the report (2)
    // with instance counts from all specified versions (1 and 2)
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains("aStringValue")
        .distributedByContains(END_EVENT, 2., END_EVENT)
        .distributedByContains(START_EVENT, 2., START_EVENT)
      .doAssert(result);
    // @formatter:on
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
      .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQUENCY_GROUP_BY_VARIABLE_BY_FLOW_NODE)
      .build();
  }

}
