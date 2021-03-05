/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single;

import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE;

public class MultiViewPropertyProcessReportIT extends AbstractProcessDefinitionIT {

  @ParameterizedTest
  @MethodSource("validMultiViewPropertyScenarios")
  public void numberResultViewEntityProcessInstanceSupportsMultipleViewProperties(final List<ViewProperty> viewProperties) {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    // modifying duration to make sure the result for count does never equal the duration average
    final int instanceDuration = 2000;
    changeProcessInstanceDuration(processInstanceDto, instanceDuration);
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      // using process instance frequency group by none as base for the setup but will modify view.properties
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    reportData.getView().setProperties(viewProperties);

    // when
    AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse = reportClient
      .evaluateNumberReport(reportData);

    // then
    // expected view properties are a Set to make sure duplicate viewProperties provided by the client
    // don't cause multiple measures
    final Set<ViewProperty> expectedMeasureViewProperties = new HashSet<>(viewProperties);
    final ReportResultResponseDto<Double> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getMeasures())
      .extracting(MeasureResponseDto::getProperty)
      .containsExactlyInAnyOrderElementsOf(expectedMeasureViewProperties);
    assertThat(resultDto.getMeasures())
      .extracting(MeasureResponseDto::getData)
      .doesNotContainNull()
      .doesNotHaveDuplicates()
      .containsExactlyInAnyOrderElementsOf(
        viewProperties.contains(ViewProperty.DURATION)
          ? Arrays.asList((double) instanceDuration, 1.)
          : Collections.singletonList(1.)
      );
    assertThat(resultDto.getMeasures())
      // for duration there should always be a aggregationType for frequency not
      .extracting(MeasureResponseDto::getAggregationType)
      .doesNotHaveDuplicates();
    assertThat(resultDto.getMeasures())
      // as this is not a userTask report the userTaskDurationTime should always be null
      .extracting(MeasureResponseDto::getUserTaskDurationTime)
      .containsOnlyNulls();
  }

  @ParameterizedTest
  @MethodSource("validMultiViewPropertyScenarios")
  public void mapResultViewEntityProcessInstanceSupportsMultipleViewProperties(final List<ViewProperty> viewProperties) {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    // modifying activity duration to make sure the result for count does never equal the duration average
    changeActivityDuration(processInstanceDto, 2000.);
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      // using flow node frequency group by flow node as base for the setup but will modify view.properties
      .setReportDataType(COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    reportData.getView().setProperties(viewProperties);

    // when
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse = reportClient
      .evaluateMapReport(reportData);

    // then
    // expected view properties are a Set to make sure duplicate viewProperties provided by the client
    // don't cause multiple measures
    final Set<ViewProperty> expectedMeasureViewProperties = new HashSet<>(viewProperties);
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getMeasures())
      .extracting(MeasureResponseDto::getProperty)
      .containsExactlyInAnyOrderElementsOf(expectedMeasureViewProperties);
    assertThat(resultDto.getMeasures())
      .extracting(MeasureResponseDto::getData)
      .doesNotContainNull()
      .doesNotHaveDuplicates();
    assertThat(resultDto.getMeasures())
      // for duration there should always be a aggregationType for frequency not
      .extracting(MeasureResponseDto::getAggregationType)
      .doesNotHaveDuplicates();
    assertThat(resultDto.getMeasures())
      // as this is not a userTask report the userTaskDurationTime should always be null
      .extracting(MeasureResponseDto::getUserTaskDurationTime)
      .containsOnlyNulls();
  }

  @ParameterizedTest
  @MethodSource("validMultiViewPropertyScenarios")
  public void hyperMapResultViewEntityProcessInstanceSupportsMultipleViewProperties(final List<ViewProperty> viewProperties) {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    // modifying activity duration to make sure the result for count does never equal the duration average
    changeUserTaskTotalDuration(processInstanceDto, 2000);
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      // using usertask frequency group by flow node as base for the setup but will modify view.properties
      .setReportDataType(USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE)
      .setGroupByDateInterval(AggregateByDateUnit.AUTOMATIC)
      .build();
    reportData.getView().setProperties(viewProperties);

    // when
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse = reportClient
      .evaluateMapReport(reportData);

    // then
    // expected view properties are a Set to make sure duplicate viewProperties provided by the client
    // don't cause multiple measures
    final Set<ViewProperty> expectedMeasureViewProperties = new HashSet<>(viewProperties);
    final List<UserTaskDurationTime> expectedUserTaskDurationTimes = expectedMeasureViewProperties.stream()
      // if there is the duration view present we should see the userTask total time as this is a user task report
      .map(viewProperty -> ViewProperty.DURATION.equals(viewProperty) ? UserTaskDurationTime.TOTAL : null)
      .collect(Collectors.toList());

    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getMeasures())
      .extracting(MeasureResponseDto::getProperty)
      .containsExactlyInAnyOrderElementsOf(expectedMeasureViewProperties);
    assertThat(resultDto.getMeasures())
      .extracting(MeasureResponseDto::getData)
      .doesNotContainNull()
      .doesNotHaveDuplicates();
    assertThat(resultDto.getMeasures())
      // for duration there should always be a aggregationType for frequency not
      .extracting(MeasureResponseDto::getAggregationType)
      .doesNotHaveDuplicates();
    assertThat(resultDto.getMeasures())
      .extracting(MeasureResponseDto::getUserTaskDurationTime)
      .containsExactlyInAnyOrderElementsOf(expectedUserTaskDurationTimes);
  }

  @ParameterizedTest
  @MethodSource("invalidMultiViewPropertyScenarios")
  public void failOnInvalidMultiViewProperties(final List<ViewProperty> viewProperties) {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      // as this is testing common report validation logic the type does not really matter
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    reportData.getView().setProperties(viewProperties);

    // when
    Response evaluationResponse = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(evaluationResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private static Stream<Arguments> validMultiViewPropertyScenarios() {
    return Stream.of(
      Arguments.of(Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.DURATION)),
      Arguments.of(Arrays.asList(ViewProperty.DURATION, ViewProperty.FREQUENCY)),
      Arguments.of(Arrays.asList(ViewProperty.DURATION, ViewProperty.FREQUENCY, ViewProperty.FREQUENCY)),
      Arguments.of(Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.FREQUENCY))
    );
  }

  private static Stream<Arguments> invalidMultiViewPropertyScenarios() {
    return Stream.of(
      Arguments.of(Arrays.asList(ViewProperty.DURATION, ViewProperty.RAW_DATA)),
      Arguments.of(Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.RAW_DATA)),
      Arguments.of(Collections.singletonList(ViewProperty.RAW_DATA))
    );
  }

}
