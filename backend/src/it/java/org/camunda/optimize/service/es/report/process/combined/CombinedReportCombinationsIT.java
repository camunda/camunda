/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.BucketUnit;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResponseDto;
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
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.INCIDENT_DUR_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_NONE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_DURATION;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_END_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_VARIABLE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CombinedReportCombinationsIT extends AbstractIT {

  @AfterEach
  public void cleanUp() {
    LocalDateUtil.reset();
  }

  @ParameterizedTest
  @MethodSource("getCombinableSingleReports")
  public void combineCombinableSingleReports(List<SingleProcessReportDefinitionRequestDto> singleReports) {
    // given
    CombinedReportDataDto combinedReportData = new CombinedReportDataDto();

    List<CombinedReportItemDto> reportIds = singleReports.stream()
      .map(report -> new CombinedReportItemDto(reportClient.createSingleProcessReport(report)))
      .collect(Collectors.toList());

    combinedReportData.setReports(reportIds);
    CombinedReportDefinitionRequestDto combinedReport = new CombinedReportDefinitionRequestDto();
    combinedReport.setData(combinedReportData);

    // when
    IdResponseDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    AuthorizedCombinedReportEvaluationResponseDto<?> result =
      reportClient.evaluateCombinedReportById(response.getId());

    assertThat(result.getReportDefinition().getData().getReports()).containsExactlyInAnyOrderElementsOf(reportIds);
  }

  private static Stream<List<SingleProcessReportDefinitionRequestDto>> getCombinableSingleReports() {
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

  private static Stream<List<SingleProcessReportDefinitionRequestDto>> createDifferentProcessDefinitionReports() {
    // different procDefs
    final SingleProcessReportDefinitionRequestDto procDefKeyReport = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto procDefKeyReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setGroupByDateInterval(AggregateByDateUnit.YEAR)
      .build();

    procDefKeyReportData.setVisualization(ProcessVisualization.BAR);
    procDefKeyReport.setData(procDefKeyReportData);

    final SingleProcessReportDefinitionRequestDto procDefAnotherKeyReport = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto procDefAnotherKeyReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
      .setProcessDefinitionKey("anotherKey")
      .setProcessDefinitionVersion("1")
      .setGroupByDateInterval(AggregateByDateUnit.YEAR)
      .build();

    procDefAnotherKeyReportData.setVisualization(ProcessVisualization.BAR);
    procDefAnotherKeyReport.setData(procDefAnotherKeyReportData);
    return Stream.of(Arrays.asList(procDefKeyReport, procDefAnotherKeyReport));
  }

  private static Stream<List<SingleProcessReportDefinitionRequestDto>> createByStartEndDateReports() {
    // byStartDate/byEndDate
    final SingleProcessReportDefinitionRequestDto byStartDate = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto procDefKeyReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setGroupByDateInterval(AggregateByDateUnit.YEAR)
      .build();

    procDefKeyReportData.setVisualization(ProcessVisualization.BAR);
    byStartDate.setData(procDefKeyReportData);

    final SingleProcessReportDefinitionRequestDto byEndDate = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto byEndDateData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_END_DATE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setGroupByDateInterval(AggregateByDateUnit.YEAR)
      .build();

    byEndDateData.setVisualization(ProcessVisualization.BAR);
    byEndDate.setData(byEndDateData);

    return Stream.of(Arrays.asList(byStartDate, byStartDate));
  }

  private static Stream<List<SingleProcessReportDefinitionRequestDto>> createByUserTaskFlowNodeDurationReports() {
    // userTaskDuration/flowNodeDuration
    final SingleProcessReportDefinitionRequestDto userTaskDuration = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto userTaskDurationData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(USER_TASK_DUR_GROUP_BY_USER_TASK)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    userTaskDurationData.setVisualization(ProcessVisualization.BAR);
    userTaskDuration.setData(userTaskDurationData);

    final SingleProcessReportDefinitionRequestDto flowNodeDuration = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto flowNodeDurationData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();

    flowNodeDurationData.setVisualization(ProcessVisualization.BAR);
    flowNodeDuration.setData(flowNodeDurationData);

    return Stream.of(Arrays.asList(userTaskDuration, flowNodeDuration));
  }

  private static Stream<List<SingleProcessReportDefinitionRequestDto>> createByNumberVariableWithSameBucketSizeReports() {
    // groupBy number variable reports with same bucket size
    final SingleProcessReportDefinitionRequestDto groupByNumberVar1 = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto groupByNumberVar1Data = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_VARIABLE)
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

    final SingleProcessReportDefinitionRequestDto groupByNumberVar2 = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto groupByNumberVar2Data = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_VARIABLE)
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

  private static Stream<List<SingleProcessReportDefinitionRequestDto>> createByInstanceDurationReports() {
    // groupByDuration
    final SingleProcessReportDefinitionRequestDto groupByDuration = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto groupByDurationData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_DURATION)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();
    groupByDuration.setData(groupByDurationData);

    final SingleProcessReportDefinitionRequestDto groupByDurationAnotherKey = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto groupByDurationDataAnotherKey = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_DURATION)
      .setProcessDefinitionKey("anotherKey")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();
    groupByDurationAnotherKey.setData(groupByDurationDataAnotherKey);

    return Stream.of(Arrays.asList(groupByDuration, groupByDurationAnotherKey));
  }

  private static Stream<List<SingleProcessReportDefinitionRequestDto>> createByInstanceDurationWithSameBucketSizeReports() {
    // groupByDuration with same bucketSize
    final SingleProcessReportDefinitionRequestDto groupByDurationBucketSize = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto groupByDurationDataBucketSize = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_DURATION)
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

    final SingleProcessReportDefinitionRequestDto groupByDurationBucketSizeAnotherKey = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto groupByDurationDataBucketSizeAnotherKey = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_DURATION)
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

  private static Stream<List<SingleProcessReportDefinitionRequestDto>> createCombinableIncidentReports() {
    // incident frequency count grouped by none
    final SingleProcessReportDefinitionRequestDto incidentFrequency1 = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto incidentFrequencyData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_FREQ_GROUP_BY_NONE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentFrequencyData1.setVisualization(ProcessVisualization.NUMBER);
    incidentFrequency1.setData(incidentFrequencyData1);

    final SingleProcessReportDefinitionRequestDto incidentFrequency2 = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto incidentFrequencyData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_FREQ_GROUP_BY_NONE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentFrequencyData2.setVisualization(ProcessVisualization.NUMBER);
    incidentFrequency2.setData(incidentFrequencyData2);

    // incident duration grouped by none
    final SingleProcessReportDefinitionRequestDto incidentDuration1 = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto incidentDurationData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_FREQ_GROUP_BY_NONE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentDurationData1.setVisualization(ProcessVisualization.NUMBER);
    incidentDuration1.setData(incidentDurationData1);

    final SingleProcessReportDefinitionRequestDto incidentDuration2 = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto incidentDurationData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_FREQ_GROUP_BY_NONE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentDurationData2.setVisualization(ProcessVisualization.NUMBER);
    incidentDuration2.setData(incidentDurationData2);

    // incident frequency count grouped by flow node
    final SingleProcessReportDefinitionRequestDto incidentFrequencyGroupedByFlowNode1 = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto incidentFrequencyGroupedByFlowNodeData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_FREQ_GROUP_BY_FLOW_NODE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentFrequencyGroupedByFlowNodeData1.setVisualization(ProcessVisualization.TABLE);
    incidentFrequencyGroupedByFlowNode1.setData(incidentFrequencyGroupedByFlowNodeData1);

    final SingleProcessReportDefinitionRequestDto incidentFrequencyGroupedByFlowNode2 = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto incidentFrequencyGroupedByFlowNodeData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_FREQ_GROUP_BY_FLOW_NODE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentFrequencyGroupedByFlowNodeData2.setVisualization(ProcessVisualization.TABLE);
    incidentFrequencyGroupedByFlowNode2.setData(incidentFrequencyGroupedByFlowNodeData2);

    // incident duration grouped by flow node
    final SingleProcessReportDefinitionRequestDto incidentDurationGroupedByFlowNode1 = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto incidentDurationGroupedByFlowNodeData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_DUR_GROUP_BY_FLOW_NODE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .build();

    incidentDurationGroupedByFlowNodeData1.setVisualization(ProcessVisualization.TABLE);
    incidentDurationGroupedByFlowNode1.setData(incidentDurationGroupedByFlowNodeData1);

    final SingleProcessReportDefinitionRequestDto incidentDurationGroupedByFlowNode2 = new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto incidentDurationGroupedByFlowNodeData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(INCIDENT_DUR_GROUP_BY_FLOW_NODE)
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
