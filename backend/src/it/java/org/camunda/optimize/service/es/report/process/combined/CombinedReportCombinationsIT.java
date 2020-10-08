/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.BucketUnit;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResultDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_DURATION;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.INCIDENT_DURATION_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.INCIDENT_FREQUENCY_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.INCIDENT_FREQUENCY_GROUP_BY_NONE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_USER_TASK;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CombinedReportCombinationsIT extends AbstractIT {

  @AfterEach
  public void cleanUp() {
    LocalDateUtil.reset();
  }

  @ParameterizedTest
  @MethodSource("getCombinableSingleReports")
  public void combineCombinableSingleReports(List<SingleProcessReportDefinitionDto> singleReports) {
    // given
    CombinedReportDataDto combinedReportData = new CombinedReportDataDto();

    List<CombinedReportItemDto> reportIds = singleReports.stream()
      .map(report -> new CombinedReportItemDto(reportClient.createSingleProcessReport(report)))
      .collect(Collectors.toList());

    combinedReportData.setReports(reportIds);
    CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
    combinedReport.setData(combinedReportData);

    // when
    IdDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    // then
    AuthorizedCombinedReportEvaluationResultDto<SingleReportResultDto> result =
      reportClient.evaluateCombinedReportById(response.getId());

    assertThat(result.getReportDefinition().getData().getReports()).containsExactlyInAnyOrderElementsOf(reportIds);
  }

  private static Stream<List<SingleProcessReportDefinitionDto>> getCombinableSingleReports() {
    return Stream.of(
      createDifferentProcessDefinitionReports(),
      createByStartEndDateReports(),
      createByUserTaskFlowNodeDurationReports(),
      createByNumberVariableWithSameBucketSizeReports(),
      createByInstanceDurationReports(),
      createByInstanceDurationWithSameBucketSizeReports(),
      createCombinableIncidentReports()
    ).flatMap(Function.identity());
  }

  private static Stream<List<SingleProcessReportDefinitionDto>> createDifferentProcessDefinitionReports() {
    // different procDefs
    final SingleProcessReportDefinitionDto procDefKeyReport = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto procDefKeyReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setGroupByDateInterval(AggregateByDateUnit.YEAR)
      .build();

    procDefKeyReportData.setVisualization(ProcessVisualization.BAR);
    procDefKeyReport.setData(procDefKeyReportData);

    final SingleProcessReportDefinitionDto procDefAnotherKeyReport = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto procDefAnotherKeyReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .setProcessDefinitionKey("anotherKey")
      .setProcessDefinitionVersion("1")
      .setGroupByDateInterval(AggregateByDateUnit.YEAR)
      .build();

    procDefAnotherKeyReportData.setVisualization(ProcessVisualization.BAR);
    procDefAnotherKeyReport.setData(procDefAnotherKeyReportData);
    return Stream.of(Arrays.asList(procDefKeyReport, procDefAnotherKeyReport));
  }

  private static Stream<List<SingleProcessReportDefinitionDto>> createByStartEndDateReports() {
    // byStartDate/byEndDate
    final SingleProcessReportDefinitionDto byStartDate = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto procDefKeyReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setGroupByDateInterval(AggregateByDateUnit.YEAR)
      .build();

    procDefKeyReportData.setVisualization(ProcessVisualization.BAR);
    byStartDate.setData(procDefKeyReportData);

    final SingleProcessReportDefinitionDto byEndDate = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto byEndDateData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setGroupByDateInterval(AggregateByDateUnit.YEAR)
      .build();

    byEndDateData.setVisualization(ProcessVisualization.BAR);
    byEndDate.setData(byEndDateData);

    return Stream.of(Arrays.asList(byStartDate, byStartDate));
  }

  private static Stream<List<SingleProcessReportDefinitionDto>> createByUserTaskFlowNodeDurationReports() {
    // userTaskDuration/flowNodeDuration
    final SingleProcessReportDefinitionDto userTaskDuration = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto userTaskDurationData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(USER_TASK_DURATION_GROUP_BY_USER_TASK)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    userTaskDurationData.setVisualization(ProcessVisualization.BAR);
    userTaskDuration.setData(userTaskDurationData);

    final SingleProcessReportDefinitionDto flowNodeDuration = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto flowNodeDurationData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();

    flowNodeDurationData.setVisualization(ProcessVisualization.BAR);
    flowNodeDuration.setData(flowNodeDurationData);

    return Stream.of(Arrays.asList(userTaskDuration, flowNodeDuration));
  }

  private static Stream<List<SingleProcessReportDefinitionDto>> createByNumberVariableWithSameBucketSizeReports() {
    // groupBy number variable reports with same bucket size
    final SingleProcessReportDefinitionDto groupByNumberVar1 = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto groupByNumberVar1Data = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setVariableType(VariableType.DOUBLE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();

    groupByNumberVar1Data.setVisualization(ProcessVisualization.BAR);
    groupByNumberVar1Data.getConfiguration().getCustomBucket().setActive(true);
    groupByNumberVar1Data.getConfiguration().getCustomBucket().setBucketSize(5.0);
    ((VariableGroupByValueDto) groupByNumberVar1Data.getGroupBy().getValue()).setName("doubleVar");
    groupByNumberVar1.setData(groupByNumberVar1Data);

    final SingleProcessReportDefinitionDto groupByNumberVar2 = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto groupByNumberVar2Data = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setVariableType(VariableType.DOUBLE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();

    groupByNumberVar2Data.setVisualization(ProcessVisualization.BAR);
    groupByNumberVar2Data.getConfiguration().getCustomBucket().setActive(true);
    groupByNumberVar2Data.getConfiguration().getCustomBucket().setBucketSize(5.0);
    ((VariableGroupByValueDto) groupByNumberVar2Data.getGroupBy().getValue()).setName("doubleVar");
    groupByNumberVar2.setData(groupByNumberVar2Data);

    return Stream.of(Arrays.asList(groupByNumberVar1, groupByNumberVar2));
  }

  private static Stream<List<SingleProcessReportDefinitionDto>> createByInstanceDurationReports() {
    // groupByDuration
    final SingleProcessReportDefinitionDto groupByDuration = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto groupByDurationData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_DURATION)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();
    groupByDuration.setData(groupByDurationData);

    final SingleProcessReportDefinitionDto groupByDurationAnotherKey = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto groupByDurationDataAnotherKey = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_DURATION)
      .setProcessDefinitionKey("anotherKey")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();
    groupByDurationAnotherKey.setData(groupByDurationDataAnotherKey);

    return Stream.of(Arrays.asList(groupByDuration, groupByDurationAnotherKey));
  }

  private static Stream<List<SingleProcessReportDefinitionDto>> createByInstanceDurationWithSameBucketSizeReports() {
    // groupByDuration with same bucketSize
    final SingleProcessReportDefinitionDto groupByDurationBucketSize = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto groupByDurationDataBucketSize = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_DURATION)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();
    groupByDurationDataBucketSize.getConfiguration().setCustomBucket(
      CustomBucketDto.builder()
        .active(true)
        .baseline(10.0D)
        .baselineUnit(BucketUnit.MILLISECOND)
        .bucketSize(100.0D)
        .bucketSizeUnit(BucketUnit.MILLISECOND)
        .build()
    );
    groupByDurationBucketSize.setData(groupByDurationDataBucketSize);

    final SingleProcessReportDefinitionDto groupByDurationBucketSizeAnotherKey = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto groupByDurationDataBucketSizeAnotherKey = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_DURATION)
      .setProcessDefinitionKey("anotherKey")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();
    groupByDurationDataBucketSizeAnotherKey.getConfiguration().setCustomBucket(
      CustomBucketDto.builder()
        .active(true)
        .baseline(10.0D)
        .baselineUnit(BucketUnit.MILLISECOND)
        .bucketSize(100.0D)
        .bucketSizeUnit(BucketUnit.MILLISECOND)
        .build()
    );
    groupByDurationBucketSizeAnotherKey.setData(groupByDurationDataBucketSizeAnotherKey);

    return Stream.of(Arrays.asList(groupByDurationBucketSize, groupByDurationBucketSizeAnotherKey));
  }

  private static Stream<List<SingleProcessReportDefinitionDto>> createCombinableIncidentReports() {
    // incident frequency count grouped by none
    final SingleProcessReportDefinitionDto incidentFrequency1 = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto incidentFrequencyData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_FREQUENCY_GROUP_BY_NONE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentFrequencyData1.setVisualization(ProcessVisualization.NUMBER);
    incidentFrequency1.setData(incidentFrequencyData1);

    final SingleProcessReportDefinitionDto incidentFrequency2 = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto incidentFrequencyData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_FREQUENCY_GROUP_BY_NONE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentFrequencyData2.setVisualization(ProcessVisualization.NUMBER);
    incidentFrequency2.setData(incidentFrequencyData2);

    // incident duration grouped by none
    final SingleProcessReportDefinitionDto incidentDuration1 = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto incidentDurationData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_FREQUENCY_GROUP_BY_NONE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentDurationData1.setVisualization(ProcessVisualization.NUMBER);
    incidentDuration1.setData(incidentDurationData1);

    final SingleProcessReportDefinitionDto incidentDuration2 = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto incidentDurationData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_FREQUENCY_GROUP_BY_NONE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentDurationData2.setVisualization(ProcessVisualization.NUMBER);
    incidentDuration2.setData(incidentDurationData2);

    // incident frequency count grouped by flow node
    final SingleProcessReportDefinitionDto incidentFrequencyGroupedByFlowNode1 = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto incidentFrequencyGroupedByFlowNodeData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_FREQUENCY_GROUP_BY_FLOW_NODE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentFrequencyGroupedByFlowNodeData1.setVisualization(ProcessVisualization.TABLE);
    incidentFrequencyGroupedByFlowNode1.setData(incidentFrequencyGroupedByFlowNodeData1);

    final SingleProcessReportDefinitionDto incidentFrequencyGroupedByFlowNode2 = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto incidentFrequencyGroupedByFlowNodeData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_FREQUENCY_GROUP_BY_FLOW_NODE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentFrequencyGroupedByFlowNodeData2.setVisualization(ProcessVisualization.TABLE);
    incidentFrequencyGroupedByFlowNode2.setData(incidentFrequencyGroupedByFlowNodeData2);

    // incident duration grouped by flow node
    final SingleProcessReportDefinitionDto incidentDurationGroupedByFlowNode1 = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto incidentDurationGroupedByFlowNodeData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_DURATION_GROUP_BY_FLOW_NODE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentDurationGroupedByFlowNodeData1.setVisualization(ProcessVisualization.TABLE);
    incidentDurationGroupedByFlowNode1.setData(incidentDurationGroupedByFlowNodeData1);

    final SingleProcessReportDefinitionDto incidentDurationGroupedByFlowNode2 = new SingleProcessReportDefinitionDto();
    final ProcessReportDataDto incidentDurationGroupedByFlowNodeData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_DURATION_GROUP_BY_FLOW_NODE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentDurationGroupedByFlowNodeData2.setVisualization(ProcessVisualization.TABLE);
    incidentDurationGroupedByFlowNode2.setData(incidentDurationGroupedByFlowNodeData2);
    return Stream.of(
      Arrays.asList(incidentFrequency1, incidentFrequency2),
      Arrays.asList(incidentDuration1, incidentDuration2),
      Arrays.asList(incidentFrequencyGroupedByFlowNode1, incidentFrequencyGroupedByFlowNode2),
      Arrays.asList(incidentDurationGroupedByFlowNode1, incidentDurationGroupedByFlowNode2)
    );
  }
}
