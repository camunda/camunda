/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.variable;

import com.google.common.collect.ImmutableList;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.BpmnModels.getTwoServiceTasksProcess;

public class CountFlowNodeFrequencyByVariableReportEvaluationIT extends AbstractProcessDefinitionIT {

  @Test
  public void simpleReportEvaluation_stringVariable() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployProcessWithFourFlownodes(Collections.singletonMap(
      "stringVar",
      "aStringValue"
    ));
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
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName()).isEqualTo("stringVar");
    assertThat(variableGroupByDto.getValue().getType()).isEqualTo(VariableType.STRING);

    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).isNotNull().hasSize(1);
    assertThat(result.getEntryForKey("aStringValue"))
      .isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(4.);
  }

  @Test
  public void simpleReportEvaluation_numberVariable() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployProcessWithFourFlownodes(Collections.singletonMap(
      "doubleVar",
      1.0
    ));
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
    assertThat(result.getData()).isNotNull().hasSize(1);
    assertThat(result.getEntryForKey("1.00"))
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
    final ProcessInstanceEngineDto processInstanceDto = deployProcessWithFourFlownodes(variables);

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
    final List<MapResultEntryDto> resultData = reportClient.evaluateMapReport(reportData).getResult().getData();

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

    final ProcessInstanceEngineDto processInstanceDto = deployProcessWithFourFlownodes(Collections.singletonMap(
      "dateVar",
      dateVarValue
    ));

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
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(numberOfInstances);
    assertThat(result.getData()).isNotNull().hasSize(numberOfInstances);

    for (int i = 0; i < numberOfInstances; i++) {
      final String expectedBucketKey = embeddedOptimizeExtension.formatToHistogramBucketKey(
        dateVarValue.plus(chronoUnit.getDuration().multipliedBy(i)),
        chronoUnit
      );
      assertThat(result.getEntryForKey(expectedBucketKey))
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

    final ProcessInstanceEngineDto processInstanceDto = deployProcessWithFourFlownodes(Collections.singletonMap(
      "dateVar",
      dateVarValue
    ));

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
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

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
    assertThat(result.getData().stream().mapToDouble(MapResultEntryDto::getValue).sum())
      .isEqualTo(4.0 * numberOfInstances); // each instance went through 4 flownodes
  }

  @Test
  public void missingVariablesAggregationWorksForUndefinedAndNullVariables() {
    // given
    // 1 process instance with 'testVar'
    ProcessInstanceEngineDto processInstanceDto = deployProcessWithFourFlownodes(Collections.singletonMap(
      "testVar",
      "withValue"
    ));

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
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then the instance withValue has passed through 4 flownodes and the 4 instances without the variable
    // have passed through 16 flownodes (4 flownodes each)
    assertThat(result.getData()).isNotNull().hasSize(2);
    assertThat(result.getEntryForKey("withValue")).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(4.);
    assertThat(result.getEntryForKey("missing")).isPresent()
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
    final ProcessInstanceEngineDto latestProcess = deployProcessWithFourFlownodes(variables);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      latestProcess.getProcessDefinitionKey(),
      ALL_VERSIONS,
      "stringVar",
      VariableType.STRING
    );
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then the result counts 2 flownodes from first definition plus 4 flownodes of the latest definition
    assertThat(result.getData()).isNotNull().hasSize(1);
    assertThat(result.getEntryForKey("aStringValue")).isPresent()
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
    final ProcessInstanceEngineDto latestProcessInst = deployProcessWithFourFlownodes(variables);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      latestProcessInst.getProcessDefinitionKey(),
      ImmutableList.of(firstProcessInst.getProcessDefinitionVersion(), latestProcessInst.getProcessDefinitionVersion()),
      "stringVar",
      VariableType.STRING
    );
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then the result counts 2 flownodes of the first decfinition plus 4 flownodes of the latest definition
    assertThat(result.getData()).isNotNull().hasSize(1);
    assertThat(result.getEntryForKey("aStringValue")).isPresent()
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

    final BpmnModelInstance subProcess = Bpmn.createExecutableProcess(subProcessKey)
      .startEvent()
      .serviceTask("MI-Body-Task")
      .camundaExpression("${true}")
      .endEvent()
      .done();
    engineIntegrationExtension.deployProcessAndGetId(subProcess);

    final BpmnModelInstance model = Bpmn.createExecutableProcess(testMIProcess)
      .name("MultiInstance")
      .startEvent("miStart")
      .parallelGateway()
      .endEvent("end1")
      .moveToLastGateway()
      .callActivity("callActivity")
      .calledElement(subProcessKey)
      .multiInstance()
      .cardinality("2")
      .multiInstanceDone()
      .endEvent("miEnd")
      .done();
    engineIntegrationExtension.deployAndStartProcessWithVariables(
      model,
      Collections.singletonMap("stringVar", "aStringValue")
    );

    engineIntegrationExtension.waitForAllProcessesToFinish();
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
      createReport(testMIProcess, "1", "stringVar", VariableType.STRING);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then the result counts 7 flownodes
    assertThat(result.getData()).isNotNull().hasSize(1);
    assertThat(result.getEntryForKey("aStringValue")).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(7.);
  }

  private ProcessInstanceEngineDto deployProcessWithFourFlownodes(final Map<String, Object> variables) {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      getTwoServiceTasksProcess("aProcess"),
      variables
    );
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
      .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQUENCY_GROUP_BY_VARIABLE)
      .build();
  }
}
