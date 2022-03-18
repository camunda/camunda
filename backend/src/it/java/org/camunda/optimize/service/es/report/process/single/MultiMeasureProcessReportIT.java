/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single;

import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.SuppressionConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MAX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_BY_ASSIGNEE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE;

public class MultiMeasureProcessReportIT extends AbstractProcessDefinitionIT {

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
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_NONE)
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
  @MethodSource("multiAggregationScenarios")
  public void numberResultSupportsMultipleViewPropertiesAlongWithMultipleAggregations(
    final AggregationDto[] aggregationTypes) {
    // given
    final Set<AggregationDto> distinctAggregationTypes = new LinkedHashSet<>(Arrays.asList(aggregationTypes));
    final List<ViewProperty> viewProperties = Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.DURATION);
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    // modifying duration to make sure the result for count does never equal the duration average
    final double instanceDuration = 2000.;
    changeProcessInstanceDuration(processInstanceDto, (int) instanceDuration);
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      // using process instance frequency group by none as base for the setup but will modify view.properties
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    reportData.getView().setProperties(viewProperties);
    reportData.getConfiguration().setAggregationTypes(aggregationTypes);

    // when
    AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse = reportClient
      .evaluateNumberReport(reportData);

    // then
    final ReportResultResponseDto<Double> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getMeasures())
      .hasSize(calculateExpectedMeasureCount(viewProperties, distinctAggregationTypes))
      .extracting(MeasureResponseDto::getAggregationType)
      .containsSequence(distinctAggregationTypes);
    assertThat(
      resultDto.getMeasures().stream().map(MeasureResponseDto::getProperty).distinct().collect(toList())
    ).containsExactlyElementsOf(viewProperties);
    assertThat(resultDto.getMeasures())
      .extracting(MeasureResponseDto::getData)
      .containsExactlyElementsOf(
        // frequency first (1), then all aggregations with the same value as we just have one instance
        DoubleStream.iterate(1, previousValue -> instanceDuration).limit(distinctAggregationTypes.size() + 1)
          .boxed().collect(Collectors.toList())
      );
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
      .setReportDataType(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
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
  @MethodSource("multiAggregationScenarios")
  public void mapResultSupportsMultipleViewPropertiesAlongWithMultipleAggregations(
    final AggregationDto[] aggregationTypes) {
    // given
    final Set<AggregationDto> distinctAggregationTypes = new LinkedHashSet<>(Arrays.asList(aggregationTypes));
    final List<ViewProperty> viewProperties = Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.DURATION);
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      // using flow node frequency group by flow node as base for the setup but will modify view.properties
      .setReportDataType(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    reportData.getView().setProperties(viewProperties);
    reportData.getConfiguration().setAggregationTypes(aggregationTypes);

    // when
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse = reportClient
      .evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getMeasures())
      .hasSize(calculateExpectedMeasureCount(viewProperties, distinctAggregationTypes))
      .extracting(MeasureResponseDto::getAggregationType)
      .containsSequence(distinctAggregationTypes);
    assertThat(
      resultDto.getMeasures().stream().map(MeasureResponseDto::getProperty).distinct().collect(toList())
    ).containsExactlyElementsOf(viewProperties);
    // no result value assertion as this was considered too much effort and the main focus here is on the measures
    // being present
  }

  @ParameterizedTest
  @MethodSource("multiAggregationAndUserTaskTimeScenarios")
  public void mapResultSupportsMultipleViewPropertiesAlongWithMultipleAggregationsAndMultipleTimes(
    final AggregationDto[] aggregationTypes,
    final UserTaskDurationTime[] userTaskDurationTimes) {
    // given
    final Set<AggregationDto> distinctAggregationTypes = new LinkedHashSet<>(Arrays.asList(aggregationTypes));
    final Set<UserTaskDurationTime> distinctUserTaskDurationTimes =
      new LinkedHashSet<>(Arrays.asList(userTaskDurationTimes));
    final List<ViewProperty> viewProperties = Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.DURATION);
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      // using user task frequency group by user task as base for the setup but will modify view.properties,
      // aggregationTypes and userTaskDurationTimes
      .setReportDataType(USER_TASK_FREQ_GROUP_BY_USER_TASK)
      .build();
    reportData.getView().setProperties(viewProperties);
    reportData.getConfiguration().setAggregationTypes(aggregationTypes);
    reportData.getConfiguration().setUserTaskDurationTimes(userTaskDurationTimes);

    // when
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getMeasures())
      .hasSize(calculateExpectedMeasureCount(
        viewProperties, distinctAggregationTypes, distinctUserTaskDurationTimes
      ))
      .extracting(MeasureResponseDto::getAggregationType)
      .containsSequence(
        // generate a sequence of the expected aggregationTypes, all distinct aggregationTypes are expected
        // to be repated for every userTaskDurationTime as each pair results in a dedicated measure
        distinctUserTaskDurationTimes.stream()
          .flatMap(userTaskDurationTime -> distinctAggregationTypes.stream())
          .collect(Collectors.toList())
      );
    assertThat(resultDto.getMeasures())
      .extracting(MeasureResponseDto::getUserTaskDurationTime)
      .containsSequence(
        // generate a sequence of all userTaskDurationTimes repeating each userTaskDuration entry
        // by the amount of aggregations used, as every aggregationType causes multiple measures per userTaskDuration
        distinctUserTaskDurationTimes.stream()
          .flatMap(userTaskDurationTime -> Stream.generate(() -> userTaskDurationTime)
            .limit(distinctAggregationTypes.size())
          ).collect(Collectors.toList())
      );
    assertThat(
      resultDto.getMeasures().stream().map(MeasureResponseDto::getProperty).distinct().collect(toList())
    ).containsExactlyElementsOf(viewProperties);
    // no result value assertion as this was considered too much effort and the main focus here is on the measures
    // being present
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
      .setReportDataType(USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE)
      .setGroupByDateInterval(AggregateByDateUnit.AUTOMATIC)
      .build();
    reportData.getView().setProperties(viewProperties);

    // when
    AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse = reportClient
      .evaluateHyperMapReport(reportData);

    // then
    // expected view properties are a Set to make sure duplicate viewProperties provided by the client
    // don't cause multiple measures
    final Set<ViewProperty> expectedMeasureViewProperties = new HashSet<>(viewProperties);
    final List<UserTaskDurationTime> expectedUserTaskDurationTimes = expectedMeasureViewProperties.stream()
      // if there is the duration view present we should see the userTask total time as this is a user task report
      .map(viewProperty -> ViewProperty.DURATION.equals(viewProperty) ? UserTaskDurationTime.TOTAL : null)
      .collect(toList());

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> resultDto = evaluationResponse.getResult();
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
  @MethodSource("multiAggregationScenarios")
  public void hyperMapResultSupportsMultipleViewPropertiesAlongWithMultipleAggregations(
    final AggregationDto[] aggregationTypes) {
    // given
    final Set<AggregationDto> distinctAggregationTypes = new LinkedHashSet<>(Arrays.asList(aggregationTypes));
    final List<ViewProperty> viewProperties = Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.DURATION);
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    // modifying activity duration to make sure the result for count does never equal the duration average
    changeUserTaskTotalDuration(processInstanceDto, 2000);
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      // using usertask frequency group by usertask startDate as base for the setup but will modify view.properties
      .setReportDataType(USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE)
      .setGroupByDateInterval(AggregateByDateUnit.AUTOMATIC)
      .build();
    reportData.getView().setProperties(viewProperties);
    reportData.getConfiguration().setAggregationTypes(aggregationTypes);

    // when
    AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse = reportClient
      .evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getMeasures())
      .hasSize(calculateExpectedMeasureCount(viewProperties, distinctAggregationTypes))
      .extracting(MeasureResponseDto::getAggregationType)
      .containsSequence(distinctAggregationTypes);
    assertThat(
      resultDto.getMeasures().stream().map(MeasureResponseDto::getProperty).distinct().collect(toList())
    ).containsExactlyElementsOf(viewProperties);
    // no result value assertion as this was considered too much effort and the main focus here is on the measures
    // being present
  }

  @ParameterizedTest
  @MethodSource("multiAggregationAndUserTaskTimeScenarios")
  public void hyperMapResultSupportsMultipleViewPropertiesAlongWithMultipleAggregationsAndMultipleUserTaskTimes(
    final AggregationDto[] aggregationTypes,
    final UserTaskDurationTime[] userTaskDurationTimes) {
    // given
    final Set<AggregationDto> distinctAggregationTypes = new LinkedHashSet<>(Arrays.asList(aggregationTypes));
    final Set<UserTaskDurationTime> distinctUserTaskDurationTimes =
      new LinkedHashSet<>(Arrays.asList(userTaskDurationTimes));
    final List<ViewProperty> viewProperties = Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.DURATION);
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      // using user task frequency group by user task distribute by assignee as base for the setup but will modify
      // view.properties, aggregationTypes and userTaskDurationTimes
      .setReportDataType(USER_TASK_FREQ_GROUP_BY_USER_TASK_BY_ASSIGNEE)
      .build();
    reportData.getView().setProperties(viewProperties);
    reportData.getConfiguration().setAggregationTypes(aggregationTypes);
    reportData.getConfiguration().setUserTaskDurationTimes(userTaskDurationTimes);

    // when
    AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse = reportClient
      .evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getMeasures())
      .hasSize(calculateExpectedMeasureCount(
        viewProperties, distinctAggregationTypes, distinctUserTaskDurationTimes
      ))
      .extracting(MeasureResponseDto::getAggregationType)
      .containsSequence(
        distinctUserTaskDurationTimes.stream()
          .flatMap(userTaskDurationTime -> distinctAggregationTypes.stream())
          .collect(Collectors.toList())
      );
    assertThat(resultDto.getMeasures())
      .extracting(MeasureResponseDto::getUserTaskDurationTime)
      .containsSequence(
        distinctUserTaskDurationTimes.stream()
          .flatMap(userTaskDurationTime -> Stream.generate(() -> userTaskDurationTime)
            .limit(distinctAggregationTypes.size())
          ).collect(Collectors.toList())
      );
    assertThat(
      resultDto.getMeasures().stream().map(MeasureResponseDto::getProperty).distinct().collect(toList())
    ).containsExactlyElementsOf(viewProperties);
    // no result value assertion as this was considered too much effort and the main focus here is on the measures
    // being present
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
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    reportData.getView().setProperties(viewProperties);

    // when
    Response evaluationResponse = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(evaluationResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private int calculateExpectedMeasureCount(final List<ViewProperty> viewProperties,
                                            final Set<AggregationDto> aggregationTypes) {
    return calculateExpectedMeasureCount(
      viewProperties, aggregationTypes, Collections.singleton(UserTaskDurationTime.TOTAL)
    );
  }

  private int calculateExpectedMeasureCount(final List<ViewProperty> viewProperties,
                                            final Set<AggregationDto> aggregationTypes,
                                            final Set<UserTaskDurationTime> userTaskDurationTimes) {
    return (viewProperties.contains(ViewProperty.FREQUENCY) ? 1 : 0)
      + (viewProperties.contains(ViewProperty.DURATION) ? aggregationTypes.size() * userTaskDurationTimes.size() : 0);
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
  private static Stream<Arguments> validMultiViewPropertyScenarios() {
    return Stream.of(
      Arguments.of(Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.DURATION)),
      Arguments.of(Arrays.asList(ViewProperty.DURATION, ViewProperty.FREQUENCY)),
      Arguments.of(Arrays.asList(ViewProperty.DURATION, ViewProperty.FREQUENCY, ViewProperty.FREQUENCY)),
      Arguments.of(Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.FREQUENCY))
    );
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
  private static Stream<Arguments> invalidMultiViewPropertyScenarios() {
    return Stream.of(
      Arguments.of(Arrays.asList(ViewProperty.DURATION, ViewProperty.RAW_DATA)),
      Arguments.of(Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.RAW_DATA)),
      Arguments.of(Collections.singletonList(ViewProperty.RAW_DATA))
    );
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
  private static Stream<Arguments> multiAggregationScenarios() {
    return Stream.of(
      Arguments.of((Object) getSupportedAggregationTypes()),
      // duplicate aggregations should be handled by only considering the first occurrence
      Arguments.of((Object) new AggregationDto[]{new AggregationDto(MIN), new AggregationDto(MIN)}),
      Arguments.of((Object) new AggregationDto[]{
        new AggregationDto(MIN),
        new AggregationDto(MAX),
        new AggregationDto(MIN)})
    );
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
  private static Stream<Arguments> multiAggregationAndUserTaskTimeScenarios() {
    return Stream.of(
      Arguments.of(getSupportedAggregationTypes(), UserTaskDurationTime.values()),
      // duplicate aggregations/userTaskTimes should be handled by only considering the first occurrence
      Arguments.of(
        new AggregationDto[]{new AggregationDto(MIN), new AggregationDto(MIN)},
        new UserTaskDurationTime[]{UserTaskDurationTime.IDLE, UserTaskDurationTime.IDLE}
      ),
      Arguments.of(
        new AggregationDto[]{new AggregationDto(MIN), new AggregationDto(MAX), new AggregationDto(MIN)},
        new UserTaskDurationTime[]{UserTaskDurationTime.IDLE, UserTaskDurationTime.WORK, UserTaskDurationTime.IDLE}
      )
    );
  }
}
