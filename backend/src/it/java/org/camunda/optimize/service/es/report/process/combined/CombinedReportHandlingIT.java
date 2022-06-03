/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.CombinedReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.BucketUnit;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedSingleReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MapMeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MAX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_END_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_DURATION;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_END_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_VARIABLE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_END_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CombinedReportHandlingIT extends AbstractIT {

  private static final String TEST_REPORT_NAME = "My foo report";

  @AfterEach
  public void cleanUp() {
    LocalDateUtil.reset();
  }

  @Test
  public void reportIsWrittenToElasticsearch() throws IOException {
    // given
    String id = createNewCombinedReport();

    // then
    GetRequest getRequest = new GetRequest(COMBINED_REPORT_INDEX_NAME).id(id);
    GetResponse getResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient().get(getRequest);

    assertThat(getResponse.isExists()).isTrue();
    CombinedReportDefinitionRequestDto definitionDto = elasticSearchIntegrationTestExtension.getObjectMapper()
      .readValue(getResponse.getSourceAsString(), CombinedReportDefinitionRequestDto.class);
    assertThat(definitionDto.getData()).isNotNull();
    CombinedReportDataDto data = definitionDto.getData();

    assertThat(data.getConfiguration()).isNotNull();
    assertThat(data.getConfiguration()).isEqualTo(new CombinedReportConfigurationDto());
    assertThat(definitionDto.getData().getReportIds()).isNotNull();
  }

  @ParameterizedTest
  @MethodSource("getUncombinableSingleReports")
  public void combineUncombinableSingleReports(List<SingleProcessReportDefinitionRequestDto> singleReports) {
    // given
    CombinedReportDataDto combinedReportData = new CombinedReportDataDto();

    List<CombinedReportItemDto> reportIds = singleReports.stream()
      .map(report -> new CombinedReportItemDto(createNewSingleReport(report)))
      .collect(Collectors.toList());

    combinedReportData.setReports(reportIds);
    CombinedReportDefinitionRequestDto combinedReport = new CombinedReportDefinitionRequestDto();
    combinedReport.setData(combinedReportData);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private static Stream<List<SingleProcessReportDefinitionRequestDto>> getUncombinableSingleReports() {
    // uncombinable visualization
    SingleProcessReportDefinitionRequestDto instanceCountStartDateYearBarChart =
      new SingleProcessReportDefinitionRequestDto(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
          .setProcessDefinitionKey("key")
          .setProcessDefinitionVersion("1")
          .setGroupByDateInterval(AggregateByDateUnit.YEAR)
          .setVisualization(ProcessVisualization.BAR)
          .build()
      );

    SingleProcessReportDefinitionRequestDto instanceCountStartDateYearLineChart =
      new SingleProcessReportDefinitionRequestDto(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
          .setProcessDefinitionKey("key")
          .setProcessDefinitionVersion("1")
          .setGroupByDateInterval(AggregateByDateUnit.YEAR)
          .setVisualization(ProcessVisualization.LINE)
          .build()
      );

    // uncombinable groupBy
    SingleProcessReportDefinitionRequestDto instanceCountByVariableBarChart =
      new SingleProcessReportDefinitionRequestDto(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setReportDataType(PROC_INST_FREQ_GROUP_BY_VARIABLE)
          .setProcessDefinitionKey("key")
          .setProcessDefinitionVersion("1")
          .setVariableName("var")
          .setVariableType(VariableType.BOOLEAN)
          .setVisualization(ProcessVisualization.BAR)
          .build()
      );

    // uncombinable view
    SingleProcessReportDefinitionRequestDto instanceDurationBstartDateYearBarChart =
      new SingleProcessReportDefinitionRequestDto(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
          .setProcessDefinitionKey("key")
          .setProcessDefinitionVersion("1")
          .setGroupByDateInterval(AggregateByDateUnit.YEAR)
          .setVisualization(ProcessVisualization.BAR)
          .build()
      );

    // groupBy number variable reports with different bucket size
    SingleProcessReportDefinitionRequestDto groupByNumberVarBucketSize5 = new SingleProcessReportDefinitionRequestDto();
    ProcessReportDataDto groupByNumberVar1Data = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setVariableType(VariableType.DOUBLE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();

    groupByNumberVar1Data.getConfiguration().getCustomBucket().setActive(true);
    groupByNumberVar1Data.getConfiguration().getCustomBucket().setBucketSize(5.0);
    ((VariableGroupByValueDto) groupByNumberVar1Data.getGroupBy().getValue()).setName("doubleVar");
    groupByNumberVarBucketSize5.setData(groupByNumberVar1Data);

    SingleProcessReportDefinitionRequestDto groupByNumberVarBucketSize10 =
      new SingleProcessReportDefinitionRequestDto();
    ProcessReportDataDto groupByNumberVar2Data = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setVariableType(VariableType.DOUBLE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();

    groupByNumberVar1Data.getConfiguration().getCustomBucket().setActive(true);
    groupByNumberVar2Data.getConfiguration().getCustomBucket().setBucketSize(10.0);
    ((VariableGroupByValueDto) groupByNumberVar2Data.getGroupBy().getValue()).setName("doubleVar");
    groupByNumberVarBucketSize10.setData(groupByNumberVar2Data);

    // groupByDuration with different bucket size
    final SingleProcessReportDefinitionRequestDto groupByDurationBucketSize100 =
      new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto groupByDurationData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_DURATION)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();
    groupByDurationData.getConfiguration().setCustomBucket(
      CustomBucketDto.builder()
        .active(true)
        .baseline(10.0D)
        .baselineUnit(BucketUnit.MILLISECOND)
        .bucketSize(100.0D)
        .bucketSizeUnit(BucketUnit.MILLISECOND)
        .build()
    );
    groupByDurationBucketSize100.setData(groupByDurationData);

    final SingleProcessReportDefinitionRequestDto groupByDurationBucketSize1 =
      new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto groupByDurationDataDifferentBucketSize = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_DURATION)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.BAR)
      .build();
    groupByDurationDataDifferentBucketSize.getConfiguration().setCustomBucket(
      CustomBucketDto.builder()
        .active(true)
        .baseline(10.0D)
        .baselineUnit(BucketUnit.MILLISECOND)
        .bucketSize(1.0D)
        .bucketSizeUnit(BucketUnit.MILLISECOND)
        .build()
    );
    groupByDurationBucketSize1.setData(groupByDurationDataDifferentBucketSize);

    // groupByFlowNodeDuration distributed by flowNode
    // as this report type is not supported at all a single report will just get combined with itself to verify that
    final SingleProcessReportDefinitionRequestDto groupByFlowNodeDurationDistributeByFlowNode =
      new SingleProcessReportDefinitionRequestDto();
    final ProcessReportDataDto groupByFlowNodeDurationDistributeByFlowNodeData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION_BY_FLOW_NODE)
      .setProcessDefinitionKey("key")
      .setProcessDefinitionVersion("1")
      .setVisualization(ProcessVisualization.TABLE)
      .build();
    groupByFlowNodeDurationDistributeByFlowNode.setData(groupByFlowNodeDurationDistributeByFlowNodeData);

    // report with multiple view properties is not supported
    SingleProcessReportDefinitionRequestDto multiViewProperty =
      new SingleProcessReportDefinitionRequestDto(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setReportDataType(PROC_INST_FREQ_GROUP_BY_NONE)
          .setProcessDefinitionKey("key")
          .setProcessDefinitionVersion("1")
          .build()
      );
    multiViewProperty.getData().getView().setProperties(ViewProperty.FREQUENCY, ViewProperty.DURATION);

    // report with multiple view properties is not supported
    SingleProcessReportDefinitionRequestDto multiAggregation =
      new SingleProcessReportDefinitionRequestDto(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setReportDataType(PROC_INST_DUR_GROUP_BY_NONE)
          .setProcessDefinitionKey("key")
          .setProcessDefinitionVersion("1")
          .build()
      );
    multiAggregation.getData()
      .getConfiguration()
      .setAggregationTypes(new AggregationDto(MIN), new AggregationDto(MAX));

    // report with multiple userTaskDurationTimes is not supported
    SingleProcessReportDefinitionRequestDto multiUserTaskDuration =
      new SingleProcessReportDefinitionRequestDto(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setReportDataType(USER_TASK_DUR_GROUP_BY_USER_TASK)
          .setProcessDefinitionKey("key")
          .setProcessDefinitionVersion("1")
          .build()
      );
    multiUserTaskDuration.getData()
      .getConfiguration()
      .setUserTaskDurationTimes(UserTaskDurationTime.IDLE, UserTaskDurationTime.TOTAL);

    return Stream.of(
      Arrays.asList(instanceCountStartDateYearBarChart, instanceCountStartDateYearLineChart),
      Arrays.asList(instanceCountByVariableBarChart, instanceCountStartDateYearBarChart),
      Arrays.asList(instanceCountStartDateYearBarChart, instanceDurationBstartDateYearBarChart),
      Arrays.asList(groupByNumberVarBucketSize5, groupByNumberVarBucketSize10),
      Arrays.asList(groupByDurationBucketSize100, groupByDurationBucketSize1),
      Arrays.asList(groupByFlowNodeDurationDistributeByFlowNode, groupByFlowNodeDurationDistributeByFlowNode),
      Arrays.asList(multiViewProperty, multiViewProperty),
      Arrays.asList(multiAggregation, multiAggregation),
      Arrays.asList(multiUserTaskDuration, multiUserTaskDuration)
    );
  }

  @Test
  public void getSingleAndCombinedReport() {
    // given
    String singleReportId = createNewSingleReport(new SingleProcessReportDefinitionRequestDto());
    String combinedReportId = createNewCombinedReport();

    // when
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    Set<String> resultSet = reports.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toSet());
    assertThat(resultSet).hasSize(2);
    assertThat(resultSet.contains(singleReportId)).isTrue();
    assertThat(resultSet.contains(combinedReportId)).isTrue();
  }

  @Test
  public void updateCombinedReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    importAllEngineEntitiesFromScratch();

    final String shouldNotBeUpdatedString = "shouldNotBeUpdated";
    String id = createNewCombinedReport();
    String singleReportId = createNewSingleNumberReport(engineDto);
    CombinedReportDefinitionRequestDto report = new CombinedReportDefinitionRequestDto();
    report.setData(createCombinedReportData(singleReportId));
    report.getData().getConfiguration().setXLabel("FooXLabel");
    report.setId(shouldNotBeUpdatedString);
    report.setLastModifier("shouldNotBeUpdatedManually");
    report.setName("MyReport");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(shouldBeIgnoredDate);
    report.setLastModified(shouldBeIgnoredDate);
    report.setOwner(shouldNotBeUpdatedString);

    // when
    updateReport(id, report);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports).hasSize(2);
    CombinedReportDefinitionRequestDto newReport = (CombinedReportDefinitionRequestDto) reports.stream()
      .filter(reportDto -> reportDto instanceof CombinedReportDefinitionRequestDto).findFirst().get();
    assertThat(newReport.getData().getReportIds()).isNotEmpty();
    assertThat(newReport.getData().getReportIds().get(0)).isEqualTo(singleReportId);
    assertThat(newReport.getData().getConfiguration().getXLabel()).isEqualTo("FooXLabel");
    assertThat(newReport.getData().getVisualization()).isEqualTo(ProcessVisualization.NUMBER);
    assertThat(newReport.getId()).isEqualTo(id);
    assertThat(newReport.getCreated()).isNotEqualTo(shouldBeIgnoredDate);
    assertThat(newReport.getLastModified()).isNotEqualTo(shouldBeIgnoredDate);
    assertThat(newReport.getName()).isEqualTo("MyReport");
    assertThat(newReport.getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  private Stream<Function<CombinedReportUpdateData, Response>> reportUpdateScenarios() {
    return Stream.of(
      data -> {
        String combinedReportId = createNewCombinedReportInCollection(data.getCollectionId());
        return addSingleReportToCombinedReport(combinedReportId, data.getSingleReportId());
      },
      data -> {
        CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
        combinedReportData.setReports(Collections.singletonList(new CombinedReportItemDto(data.getSingleReportId())));
        CombinedReportDefinitionRequestDto combinedReport = new CombinedReportDefinitionRequestDto();
        combinedReport.setData(combinedReportData);
        combinedReport.setCollectionId(data.getCollectionId());
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .buildCreateCombinedReportRequest(combinedReport)
          .execute();
      }
    );
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updateCombinedReportCollectionReportCanBeAddedToSameCollectionCombinedReport(Function<CombinedReportUpdateData, Response> scenario) {
    // given
    String collectionId = collectionClient.createNewCollection();
    final String singleReportId = reportClient.createEmptySingleProcessReportInCollection(collectionId);

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(singleReportId, collectionId));

    // then
    assertThat(updateResponse.getStatus()).isIn(
      Arrays.asList(Response.Status.OK.getStatusCode(), Response.Status.NO_CONTENT.getStatusCode()
      ));
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updateCombinedReportCollectionReportCannotBeAddedToOtherCollectionCombinedReport(Function<CombinedReportUpdateData, Response> scenario) {
    // given
    String collectionId1 = collectionClient.createNewCollection();
    String collectionId2 = collectionClient.createNewCollection();
    final String singleReportId = reportClient.createEmptySingleProcessReportInCollection(collectionId2);

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(singleReportId, collectionId1));

    // then
    assertThat(updateResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updateCombinedReportCollectionReportCannotBeAddedToPrivateCombinedReport(Function<CombinedReportUpdateData, Response> scenario) {
    // given
    String collectionId = collectionClient.createNewCollection();
    final String singleReportId = reportClient.createEmptySingleProcessReportInCollection(collectionId);

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(singleReportId, null));

    // then
    assertThat(updateResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updatePrivateCombinedReportReportCannotBeAddedToCollectionCombinedReport(Function<CombinedReportUpdateData, Response> scenario) {
    // given
    String collectionId = collectionClient.createNewCollection();
    final String singleReportId = reportClient.createEmptySingleProcessReportInCollection(null);

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(singleReportId, collectionId));

    // then
    assertThat(updateResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("reportUpdateScenarios")
  public void updatePrivateCombinedReportAddingOtherUsersPrivateReportFails(
    Function<CombinedReportUpdateData, Response> scenario) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String reportId = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateSingleProcessReportRequest()
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();

    // when
    Response updateResponse = scenario.apply(new CombinedReportUpdateData(reportId, null));


    // then
    assertThat(updateResponse.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void addUncombinableReportThrowsError() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String numberReportId = createNewSingleNumberReport(engineDto);
    String rawReportId = createNewSingleRawReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String combinedReportId = createNewCombinedReport();
    CombinedReportDefinitionRequestDto combinedReport = new CombinedReportDefinitionRequestDto();
    combinedReport.setData(createCombinedReportData(numberReportId, rawReportId));
    ErrorResponseDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReport, true)
      .execute(ErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    assertThat(response.getErrorCode()).isEqualTo("reportsNotCombinable");
  }

  @Test
  public void reportEvaluationReturnsMetaData() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    String reportId = createNewCombinedReport();
    CombinedReportDefinitionRequestDto report = new CombinedReportDefinitionRequestDto();
    report.setData(createCombinedReportData(singleReportId));
    report.setName("name");
    OffsetDateTime now = OffsetDateTime.now();
    updateReport(reportId, report);

    // when
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateCombinedReportById(
        reportId);

    // then
    assertThat(result.getReportDefinition().getId()).isEqualTo(reportId);
    assertThat(result.getReportDefinition().getName()).isEqualTo("name");
    assertThat(result.getReportDefinition().getOwner()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(result.getReportDefinition().getCreated().truncatedTo(ChronoUnit.DAYS))
      .isEqualTo(now.truncatedTo(ChronoUnit.DAYS));
    assertThat(result.getReportDefinition().getLastModifier()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(result.getReportDefinition().getLastModified().truncatedTo(ChronoUnit.DAYS))
      .isEqualTo(now.truncatedTo(ChronoUnit.DAYS));
    assertThat(result.getResult().getData()).isNotNull();
    assertThat(result.getReportDefinition().getData().getReportIds()).hasSize(1);
    assertThat(result.getReportDefinition().getData().getReportIds().get(0)).isEqualTo(singleReportId);
    assertThat(result.getReportDefinition().getData().getConfiguration())
      .isEqualTo(new CombinedReportConfigurationDto());
  }

  @Test
  public void unsavedReportEvaluationWithPaginationReturnsError() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    Map<String, Object> params = ImmutableMap.of(
      PaginationRequestDto.LIMIT_PARAM, 10,
      PaginationRequestDto.OFFSET_PARAM, 20
    );

    // when
    final OptimizeRequestExecutor optimizeRequestExecutor = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(createCombinedReportData(singleReportId));
    optimizeRequestExecutor.addQueryParams(params);
    final Response response = optimizeRequestExecutor.execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void savedReportEvaluationWithPaginationReturnsError() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    PaginationRequestDto paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setOffset(10);
    paginationRequestDto.setLimit(10);
    final String combinedReportId = reportClient.createNewCombinedReport(singleReportId);

    // when
    final OptimizeRequestExecutor optimizeRequestExecutor = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSavedReportRequest(combinedReportId, paginationRequestDto);
    final Response response = optimizeRequestExecutor.execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void deleteCombinedReport() {
    // given
    String reportId = createNewCombinedReport();

    // when
    deleteReport(reportId);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports).isEmpty();
  }

  @Test
  public void canSaveAndEvaluateCombinedReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String reportId = createNewCombinedReport(singleReportId, singleReportId2);
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateCombinedReportById(
        reportId);

    // then
    assertThat(result.getReportDefinition().getId()).isEqualTo(reportId);
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap = result.getResult()
      .getData();
    assertThat(resultMap).hasSize(2);
    List<MapResultEntryDto> flowNodeToCount = resultMap.get(singleReportId).getResult().getFirstMeasureData();
    assertThat(flowNodeToCount).hasSize(3);
    List<MapResultEntryDto> flowNodeToCount2 = resultMap.get(singleReportId2).getResult().getFirstMeasureData();
    assertThat(flowNodeToCount2).hasSize(3);
  }

  @Test
  public void combinedReportsCanBeEvaluatedWithAdditionalFiltersAppliedToContainedReports_stateFilters() {
    // given
    ProcessInstanceEngineDto runningInstanceEngineDto = deployAndStartSimpleUserTaskProcess();
    ProcessInstanceEngineDto completedInstanceEngineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(completedInstanceEngineDto.getId());
    String runningInstanceReportId = createNewSingleMapReport(runningInstanceEngineDto);
    String completedInstanceReportId = createNewSingleMapReport(completedInstanceEngineDto);
    importAllEngineEntitiesFromScratch();

    // when no filters are applied
    String combinedReportId = createNewCombinedReport(runningInstanceReportId, completedInstanceReportId);
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateCombinedReportById(combinedReportId);

    // then both reports contain the expected data for their single instance
    assertThat(result.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(result.getResult().getInstanceCount()).isEqualTo(2);
    assertThat(result.getResult().getData().entrySet()).hasSize(2);

    ReportResultResponseDto<List<MapResultEntryDto>> resultRunningInstance =
      result.getResult().getData().get(runningInstanceReportId).getResult();
    assertThat(resultRunningInstance.getInstanceCount()).isEqualTo(1);
    assertThat(resultRunningInstance.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(resultRunningInstance.getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(null, 1., 1.);

    ReportResultResponseDto<List<MapResultEntryDto>> resultCompletedInstance = result.getResult()
      .getData()
      .get(completedInstanceReportId)
      .getResult();
    assertThat(resultCompletedInstance.getInstanceCount()).isEqualTo(1);
    assertThat(resultCompletedInstance.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getResult().getData().get(completedInstanceReportId).getResult().getFirstMeasureData())
      .hasSize(3)
      .extracting(MapResultEntryDto::getValue)
      .doesNotContainNull();

    // when completed instance filter applied
    AdditionalProcessReportEvaluationFilterDto filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(ProcessFilterBuilder.filter()
                          .completedInstancesOnly().add()
                          .buildList());
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> filteredResult =
      reportClient.evaluateCombinedReportByIdWithAdditionalFilters(combinedReportId, filterDto);

    // then only the completed instance report returns data for its instance
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(result.getResult().getInstanceCount()).isEqualTo(2);
    assertThat(filteredResult.getResult().getData().entrySet()).hasSize(2);

    resultRunningInstance = result.getResult().getData().get(runningInstanceReportId).getResult();
    assertThat(resultRunningInstance.getInstanceCount()).isEqualTo(1);
    assertThat(resultRunningInstance.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(filteredResult.getResult().getData().get(runningInstanceReportId).getResult().getFirstMeasureData())
      .hasSize(3)
      .extracting(MapResultEntryDto::getValue)
      .containsOnlyNulls();

    resultCompletedInstance = result.getResult().getData().get(completedInstanceReportId).getResult();
    assertThat(resultCompletedInstance.getInstanceCount()).isEqualTo(1);
    assertThat(resultCompletedInstance.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(filteredResult.getResult().getData().get(completedInstanceReportId).getResult().getFirstMeasureData())
      .hasSize(3)
      .extracting(MapResultEntryDto::getValue)
      .doesNotContainNull();
  }

  @Test
  public void combinedReportsCanBeEvaluatedWithAdditionalFiltersAppliedToContainedReports_identityFilters() {
    // given one process with an assignee and one with a candidate group
    final ProcessInstanceEngineDto instanceWithAssignee =
      engineIntegrationExtension.deployAndStartProcess(BpmnModels.getUserTaskDiagramWithAssignee(DEFAULT_USERNAME));
    engineIntegrationExtension.finishAllRunningUserTasks(instanceWithAssignee.getId());
    final String candidateGroupId = "candidateGroupId";
    final ProcessInstanceEngineDto instanceWithCandidateGroup =
      engineIntegrationExtension.deployAndStartProcess(BpmnModels.getUserTaskDiagramWithCandidateGroup(candidateGroupId));
    engineIntegrationExtension.completeUserTaskWithoutClaim(instanceWithCandidateGroup.getId());

    final String reportWithAssignee = createNewSingleMapReport(instanceWithAssignee);
    final String reportWithCandidateGroup = createNewSingleMapReport(instanceWithCandidateGroup);
    final String combinedReportId = createNewCombinedReport(reportWithAssignee, reportWithCandidateGroup);

    importAllEngineEntitiesFromScratch();

    // when assignee filter applied
    AdditionalProcessReportEvaluationFilterDto filterDto =
      new AdditionalProcessReportEvaluationFilterDto(
        ProcessFilterBuilder.filter()
          .assignee()
          .operator(MembershipFilterOperator.IN)
          .id(DEFAULT_USERNAME)
          .add()
          .buildList());
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> filteredResult =
      reportClient.evaluateCombinedReportByIdWithAdditionalFilters(combinedReportId, filterDto);

    // then
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet()).hasSize(2);
    assertThat(filteredResult.getResult().getInstanceCount()).isEqualTo(1);

    ReportResultResponseDto<List<MapResultEntryDto>> resultWithCandidateGroup =
      filteredResult.getResult().getData().get(reportWithCandidateGroup).getResult();
    assertThat(resultWithCandidateGroup.getInstanceCount()).isZero();
    assertThat(resultWithCandidateGroup.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(resultWithCandidateGroup.getFirstMeasureData())
      .hasSize(3)
      .extracting(MapResultEntryDto::getValue)
      .containsOnlyNulls();

    ReportResultResponseDto<List<MapResultEntryDto>> resultWithAssignee = filteredResult.getResult()
      .getData()
      .get(reportWithAssignee)
      .getResult();
    assertThat(resultWithAssignee.getInstanceCount()).isEqualTo(1);
    assertThat(resultWithAssignee.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(resultWithAssignee.getFirstMeasureData())
      .hasSize(3)
      .extracting(MapResultEntryDto::getValue)
      .doesNotContainNull();

    // when candidate group filter applied
    filterDto = new AdditionalProcessReportEvaluationFilterDto(
      ProcessFilterBuilder.filter()
        .candidateGroups()
        .operator(MembershipFilterOperator.IN)
        .id(candidateGroupId)
        .add()
        .buildList());
    filteredResult = reportClient.evaluateCombinedReportByIdWithAdditionalFilters(combinedReportId, filterDto);

    // then
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet()).hasSize(2);
    assertThat(filteredResult.getResult().getInstanceCount()).isEqualTo(1);

    resultWithAssignee = filteredResult.getResult().getData().get(reportWithAssignee).getResult();
    assertThat(resultWithAssignee.getInstanceCount()).isZero();
    assertThat(resultWithAssignee.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(filteredResult.getResult().getData().get(reportWithAssignee).getResult().getFirstMeasureData())
      .hasSize(3)
      .extracting(MapResultEntryDto::getValue)
      .containsOnlyNulls();

    resultWithCandidateGroup = filteredResult.getResult().getData().get(reportWithCandidateGroup).getResult();
    assertThat(resultWithCandidateGroup.getInstanceCount()).isEqualTo(1);
    assertThat(resultWithCandidateGroup.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(resultWithCandidateGroup.getFirstMeasureData())
      .hasSize(3)
      .extracting(MapResultEntryDto::getValue)
      .doesNotContainNull();
  }

  @Test
  public void combinedReportsCanBeEvaluatedWithAdditionalFiltersAppliedToContainedReportsWithExistingReportFilters() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    AdditionalProcessReportEvaluationFilterDto singleReportFilterDto = new AdditionalProcessReportEvaluationFilterDto();
    singleReportFilterDto.setFilter(ProcessFilterBuilder.filter()
                                      .fixedInstanceEndDate()
                                      .end(OffsetDateTime.now().plusDays(1))
                                      .add()
                                      .buildList());
    String singleReportIdA = createNewSingleMapReportWithFilter(engineDto, singleReportFilterDto);
    String singleReportIdB = createNewSingleMapReportWithFilter(engineDto, singleReportFilterDto);
    importAllEngineEntitiesFromScratch();

    // when no additional filters are applied
    String combinedReportId = createNewCombinedReport(singleReportIdA, singleReportIdB);
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateCombinedReportById(combinedReportId);

    // then both reports contain the expected data with no null values
    assertThat(result.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(result.getResult().getData().entrySet())
      .hasSize(2)
      .allSatisfy(reportResult -> assertThat(reportResult.getValue().getResult().getFirstMeasureData())
        .hasSize(3)
        .extracting(MapResultEntryDto::getValue)
        .doesNotContainNull()
      );

    // when future start date filter applied
    AdditionalProcessReportEvaluationFilterDto filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(ProcessFilterBuilder.filter()
                          .fixedInstanceStartDate().start(OffsetDateTime.now().plusDays(1)).add()
                          .buildList());
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> filteredResult =
      reportClient.evaluateCombinedReportByIdWithAdditionalFilters(combinedReportId, filterDto);

    // then the data values are now null
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet())
      .hasSize(2)
      .allSatisfy(reportResult -> assertThat(reportResult.getValue().getResult().getFirstMeasureData())
        .hasSize(3)
        .extracting(MapResultEntryDto::getValue)
        .containsOnlyNulls()
      );
  }

  @Test
  public void combinedReportsCanBeEvaluatedWithAdditionalFilters_allDataFilteredOutWithMultipleFilters() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportIdA = createNewSingleMapReport(engineDto);
    String singleReportIdB = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when no filters are applied
    String combinedReportId = createNewCombinedReport(singleReportIdA, singleReportIdB);
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateCombinedReportById(combinedReportId);

    // then both reports contain the expected data with no null values
    assertThat(result.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(result.getResult().getData().entrySet())
      .hasSize(2)
      .allSatisfy(reportResult -> assertThat(reportResult.getValue().getResult().getFirstMeasureData())
        .hasSize(3)
        .extracting(MapResultEntryDto::getValue)
        .doesNotContainNull()
      );

    // when running and completed instance filters applied
    AdditionalProcessReportEvaluationFilterDto filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(ProcessFilterBuilder.filter()
                          .runningInstancesOnly().add()
                          .completedInstancesOnly().add()
                          .buildList());
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> filteredResult =
      reportClient.evaluateCombinedReportByIdWithAdditionalFilters(combinedReportId, filterDto);

    // then the data values are now null
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet())
      .hasSize(2)
      .allSatisfy(reportResult -> assertThat(reportResult.getValue().getResult().getFirstMeasureData())
        .hasSize(3)
        .extracting(MapResultEntryDto::getValue)
        .containsOnlyNulls()
      );
  }

  @Test
  public void combinedReportsCanBeEvaluatedWithAdditionalFilters_filterVariableNotPresentForEitherReport() {
    // given
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartSimpleUserTaskProcess();
    String report1 = createNewSingleMapReport(processInstanceEngineDto);
    String report2 = createNewSingleMapReport(processInstanceEngineDto);
    importAllEngineEntitiesFromScratch();

    // when no filters are applied
    String combinedReportId = createNewCombinedReport(report1, report2);
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateCombinedReportByIdWithAdditionalFilters(combinedReportId, null);

    // then both reports contain the expected data instance
    assertThat(result.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(result.getResult().getData().entrySet()).hasSize(2);
    assertThat(result.getResult().getData().get(report1).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(null, 1., 1.);
    assertThat(result.getResult().getData().get(report2).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(null, 1., 1.);

    // when variable filter applied
    AdditionalProcessReportEvaluationFilterDto filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(buildStringVariableFilter("someVarName", "varValue"));
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> filteredResult =
      reportClient.evaluateCombinedReportByIdWithAdditionalFilters(combinedReportId, filterDto);

    // then the filter is ignored as the filter name/type is not known
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet()).hasSize(2);
    assertThat(filteredResult.getResult().getData().get(report1).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(null, 1., 1.);
    assertThat(filteredResult.getResult().getData().get(report2).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(null, 1., 1.);
  }

  @Test
  public void combinedReportsCanBeEvaluatedWithAdditionalFilters_filterVariableExistsInOneReport() {
    // given
    final String varName = "var1";
    ProcessInstanceEngineDto variableInstance = deployAndStartSimpleUserTaskProcessWithVariables(ImmutableMap.of(
      varName, "val1"
    ));
    ProcessInstanceEngineDto noVariableInstance = deployAndStartSimpleUserTaskProcess();
    String variableReport = createNewSingleMapReport(variableInstance);
    String noVariableReport = createNewSingleMapReport(noVariableInstance);
    importAllEngineEntitiesFromScratch();

    // when no filters are applied
    String combinedReportId = createNewCombinedReport(variableReport, noVariableReport);
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateCombinedReportByIdWithAdditionalFilters(combinedReportId, null);

    // then both reports contain the expected data instance
    assertThat(result.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(result.getResult().getData().entrySet()).hasSize(2);
    assertThat(result.getResult().getData().get(variableReport).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(null, 1., 1.);
    assertThat(result.getResult().getData().get(noVariableReport).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(null, 1., 1.);

    // when variable filter applied
    AdditionalProcessReportEvaluationFilterDto filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(buildStringVariableFilter(varName, "someOtherValue"));
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> filteredResult =
      reportClient.evaluateCombinedReportByIdWithAdditionalFilters(combinedReportId, filterDto);

    // then the filter is applied to the report where it exists and ignored in the no variable report
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet()).hasSize(2);
    assertThat(filteredResult.getResult().getData().get(variableReport).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsOnlyNulls();
    assertThat(filteredResult.getResult().getData().get(noVariableReport).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(null, 1., 1.);
  }

  @Test
  public void combinedReportsCanBeEvaluatedWithAdditionalFilters_filterVariableExistsInBothReports() {
    // given
    final String varName = "var1";
    ProcessInstanceEngineDto instance1 = deployAndStartSimpleUserTaskProcessWithVariables(ImmutableMap.of(
      varName, "val1"
    ));
    ProcessInstanceEngineDto instance2 = deployAndStartSimpleUserTaskProcessWithVariables(ImmutableMap.of(
      varName, "val2"
    ));
    String report1 = createNewSingleMapReport(instance1);
    String report2 = createNewSingleMapReport(instance2);
    importAllEngineEntitiesFromScratch();

    // when no filters are applied
    String combinedReportId = createNewCombinedReport(report1, report2);
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateCombinedReportByIdWithAdditionalFilters(combinedReportId, null);

    // then both reports contain the expected data instance
    assertThat(result.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(result.getResult().getData().entrySet()).hasSize(2);
    assertThat(result.getResult().getData().get(report1).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(null, 1., 1.);
    assertThat(result.getResult().getData().get(report2).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(null, 1., 1.);

    // when variable filter applied with other value
    AdditionalProcessReportEvaluationFilterDto filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(buildStringVariableFilter(varName, "someOtherValue"));
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> filteredResult =
      reportClient.evaluateCombinedReportByIdWithAdditionalFilters(combinedReportId, filterDto);

    // then the filter is applied to both reports correctly
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet()).hasSize(2);
    assertThat(filteredResult.getResult().getData().get(report1).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsOnlyNulls();
    assertThat(filteredResult.getResult().getData().get(report2).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsOnlyNulls();

    // when variable filter applied that matches value form single report
    filterDto = new AdditionalProcessReportEvaluationFilterDto();
    filterDto.setFilter(buildStringVariableFilter(varName, "val1"));
    filteredResult = reportClient.evaluateCombinedReportByIdWithAdditionalFilters(combinedReportId, filterDto);

    // then the filter is applied to both reports correctly
    assertThat(filteredResult.getReportDefinition().getId()).isEqualTo(combinedReportId);
    assertThat(filteredResult.getResult().getData().entrySet()).hasSize(2);
    assertThat(filteredResult.getResult().getData().get(report1).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(null, 1., 1.);
    assertThat(filteredResult.getResult().getData().get(report2).getResult().getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsOnlyNulls();
  }

  @Test
  public void canSaveAndEvaluateCombinedReportsWithUserTaskDurationReportsOfDifferentDurationViewProperties() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String reportId = createNewCombinedReport(totalDurationReportId, idleDurationReportId);
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateCombinedReportById(
        reportId);

    // then
    assertThat(result.getReportDefinition().getId()).isEqualTo(reportId);
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap =
      result.getResult().getData();
    assertThat(resultMap).hasSize(2);
    List<MapResultEntryDto> userTaskCount1 = resultMap.get(totalDurationReportId)
      .getResult()
      .getFirstMeasureData();
    assertThat(userTaskCount1).hasSize(1);
    List<MapResultEntryDto> userTaskCount2 = resultMap.get(idleDurationReportId)
      .getResult()
      .getFirstMeasureData();
    assertThat(userTaskCount2).hasSize(1);
  }

  @Test
  public void canSaveAndEvaluateCombinedReportsWithUserTaskDurationAndProcessDurationReports() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    String userTaskTotalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String flowNodeDurationReportId = createNewSingleDurationMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String reportId = createNewCombinedReport(userTaskTotalDurationReportId, flowNodeDurationReportId);
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateCombinedReportById(
        reportId);

    // then
    assertThat(result.getReportDefinition().getId()).isEqualTo(reportId);
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap =
      result.getResult().getData();
    assertThat(resultMap).hasSize(2);
    List<MapResultEntryDto> userTaskCount1 = resultMap.get(userTaskTotalDurationReportId)
      .getResult()
      .getFirstMeasureData();
    assertThat(userTaskCount1).hasSize(1);
    List<MapResultEntryDto> userTaskCount2 = resultMap.get(flowNodeDurationReportId)
      .getResult()
      .getFirstMeasureData();
    assertThat(userTaskCount2).hasSize(3);
  }

  @Test
  public void canSaveAndEvaluateCombinedReportsWithStartAndEndDateGroupedReports() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));

    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());

    String singleReportId1 = createNewSingleReportGroupByEndDate(engineDto, AggregateByDateUnit.DAY);
    String singleReportId2 = createNewSingleReportGroupByStartDate(engineDto, AggregateByDateUnit.DAY);

    importAllEngineEntitiesFromScratch();

    // when
    final String combinedReportId = createNewCombinedReport(singleReportId1, singleReportId2);
    final AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>>
      result = reportClient.evaluateCombinedReportById(combinedReportId);

    // then
    final Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap =
      result.getResult()
        .getData();
    assertThat(resultMap)
      .isNotNull()
      .hasSize(2);
    assertThat(resultMap.keySet()).containsExactlyInAnyOrder(singleReportId1, singleReportId2);

    final ReportResultResponseDto<List<MapResultEntryDto>> result1 = resultMap.get(singleReportId1)
      .getResult();
    final List<MapResultEntryDto> resultData1 = result1.getFirstMeasureData();
    assertThat(resultData1).isNotNull().hasSize(1);

    final ReportResultResponseDto<List<MapResultEntryDto>> result2 = resultMap.get(singleReportId2)
      .getResult();
    final List<MapResultEntryDto> resultData2 = result2.getFirstMeasureData();
    assertThat(resultData2)
      .isNotNull()
      .hasSize(3);
  }

  @Test
  public void reportsThatCantBeEvaluatedAreIgnored() {
    // given
    deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleReport(new SingleProcessReportDefinitionRequestDto());
    importAllEngineEntitiesFromScratch();

    // when
    String reportId = createNewCombinedReport(singleReportId);
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateCombinedReportById(
        reportId);

    // then
    assertThat(result.getReportDefinition().getId()).isEqualTo(reportId);
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap =
      result.getResult().getData();
    assertThat(resultMap).isEmpty();
  }

  @Test
  public void deletedSingleReportsAreRemovedFromCombinedReportWhenForced() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportIdToDelete = createNewSingleMapReport(engineDto);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToDelete, remainingSingleReportId);
    deleteReport(singleReportIdToDelete, true);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    final Set<String> resultSet = reports.stream().map(ReportDefinitionDto::getId).collect(Collectors.toSet());
    assertThat(resultSet)
      .containsExactlyInAnyOrder(combinedReportId, remainingSingleReportId);

    assertThat(reports)
      .filteredOn(reportDto -> reportDto instanceof CombinedReportDefinitionRequestDto)
      .singleElement()
      .satisfies(reportDto -> {
        final CombinedReportDataDto combinedReportData = ((CombinedReportDefinitionRequestDto) reportDto)
          .getData();
        assertThat(combinedReportData.getReportIds())
          .containsExactly(remainingSingleReportId);
      });
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWithVisualizeAsChangedWhenForced() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    countFlowNodeFrequencyGroupByFlowNode.setVisualization(ProcessVisualization.TABLE);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report, true);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    final Set<String> resultSet = reports.stream().map(ReportDefinitionDto::getId).collect(Collectors.toSet());
    assertThat(resultSet)
      .hasSize(3)
      .containsExactlyInAnyOrder(combinedReportId, singleReportIdToUpdate, remainingSingleReportId);

    assertThat(reports)
      .filteredOn(reportDto -> reportDto instanceof CombinedReportDefinitionRequestDto)
      .singleElement()
      .satisfies(reportDto -> {
        final CombinedReportDataDto combinedReportData = ((CombinedReportDefinitionRequestDto) reportDto)
          .getData();
        assertThat(combinedReportData.getReportIds())
          .containsExactly(remainingSingleReportId);
      });
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWithGroupByChangedWhenForced() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    countFlowNodeFrequencyGroupByFlowNode.getGroupBy().setType(ProcessGroupByType.START_DATE);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report, true);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    final Set<String> resultSet = reports.stream().map(ReportDefinitionDto::getId).collect(Collectors.toSet());
    assertThat(resultSet)
      .hasSize(3)
      .containsExactlyInAnyOrder(combinedReportId, singleReportIdToUpdate, remainingSingleReportId);

    assertThat(reports)
      .filteredOn(reportDto -> reportDto instanceof CombinedReportDefinitionRequestDto)
      .singleElement()
      .satisfies(reportDto -> {
        final CombinedReportDataDto combinedReportData = ((CombinedReportDefinitionRequestDto) reportDto)
          .getData();
        assertThat(combinedReportData.getReportIds())
          .containsExactly(remainingSingleReportId);
      });
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWithViewChangedWhenForced() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    String remainingSingleReportId = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    countFlowNodeFrequencyGroupByFlowNode.getView().setEntity(ProcessViewEntity.PROCESS_INSTANCE);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report, true);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    final Set<String> resultSet = reports.stream().map(ReportDefinitionDto::getId).collect(Collectors.toSet());
    assertThat(resultSet)
      .hasSize(3)
      .containsExactlyInAnyOrder(combinedReportId, singleReportIdToUpdate, remainingSingleReportId);

    assertThat(reports)
      .filteredOn(reportDto -> reportDto instanceof CombinedReportDefinitionRequestDto)
      .singleElement()
      .satisfies(reportDto -> {
        final CombinedReportDataDto combinedReportData = ((CombinedReportDefinitionRequestDto) reportDto)
          .getData();
        assertThat(combinedReportData.getReportIds())
          .containsExactly(remainingSingleReportId);
      });
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWhenMultiViewPropertyAdded() {
    // given
    final ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    final ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    final String singleReportIdToUpdate = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    final String remainingSingleReportId = createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
    importAllEngineEntitiesFromScratch();

    // when
    final String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    final SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    countFlowNodeFrequencyGroupByFlowNode.getView().setProperties(ViewProperty.FREQUENCY, ViewProperty.DURATION);
    report.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportIdToUpdate, report, true);
    final List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    final Set<String> resultSet = reports.stream().map(ReportDefinitionDto::getId).collect(Collectors.toSet());
    assertThat(resultSet)
      .hasSize(3)
      .containsExactlyInAnyOrder(combinedReportId, singleReportIdToUpdate, remainingSingleReportId);

    assertThat(reports)
      .filteredOn(reportDto -> reportDto instanceof CombinedReportDefinitionRequestDto)
      .singleElement()
      .satisfies(reportDto -> {
        final CombinedReportDataDto combinedReportData = ((CombinedReportDefinitionRequestDto) reportDto)
          .getData();
        assertThat(combinedReportData.getReportIds())
          .containsExactly(remainingSingleReportId);
      });
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWhenMultiAggregationAdded() {
    // given
    final ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    final ProcessReportDataDto userTaskDurationByTaskReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(USER_TASK_DUR_GROUP_BY_USER_TASK)
      .build();
    final String singleReportIdToUpdate = createNewSingleMapReport(userTaskDurationByTaskReportData);
    final String remainingSingleReportId = createNewSingleMapReport(userTaskDurationByTaskReportData);
    importAllEngineEntitiesFromScratch();

    // when
    final String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    final SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    userTaskDurationByTaskReportData.getConfiguration()
      .setAggregationTypes(new AggregationDto(MIN), new AggregationDto(MAX));
    report.setData(userTaskDurationByTaskReportData);
    updateReport(singleReportIdToUpdate, report, true);
    final List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    final Set<String> resultSet = reports.stream().map(ReportDefinitionDto::getId).collect(Collectors.toSet());
    assertThat(resultSet)
      .hasSize(3)
      .contains(combinedReportId);
    Optional<CombinedReportDefinitionRequestDto> combinedReport = reports.stream()
      .filter(reportDto -> reportDto instanceof CombinedReportDefinitionRequestDto)
      .map(reportDto -> (CombinedReportDefinitionRequestDto) reportDto)
      .findFirst();
    assertThat(combinedReport).isPresent();
    CombinedReportDataDto dataDto = combinedReport.get().getData();
    assertThat(dataDto.getReportIds()).hasSize(1);
    assertThat(dataDto.getReportIds().get(0)).isEqualTo(remainingSingleReportId);
  }

  @Test
  public void singleReportsAreRemovedFromCombinedReportOnReportUpdateWhenMultiUserTaskTimesAdded() {
    // given
    final ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    final ProcessReportDataDto userTaskDurationByTaskReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(USER_TASK_DUR_GROUP_BY_USER_TASK)
      .build();
    final String singleReportIdToUpdate = createNewSingleMapReport(userTaskDurationByTaskReportData);
    final String remainingSingleReportId = createNewSingleMapReport(userTaskDurationByTaskReportData);
    importAllEngineEntitiesFromScratch();

    // when
    final String combinedReportId = createNewCombinedReport(singleReportIdToUpdate, remainingSingleReportId);
    final SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    userTaskDurationByTaskReportData.getConfiguration().setUserTaskDurationTimes(
      UserTaskDurationTime.IDLE, UserTaskDurationTime.WORK
    );
    report.setData(userTaskDurationByTaskReportData);
    updateReport(singleReportIdToUpdate, report, true);
    final List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    final Set<String> resultSet = reports.stream().map(ReportDefinitionDto::getId).collect(Collectors.toSet());
    assertThat(resultSet)
      .hasSize(3)
      .contains(combinedReportId);
    Optional<CombinedReportDefinitionRequestDto> combinedReport = reports.stream()
      .filter(reportDto -> reportDto instanceof CombinedReportDefinitionRequestDto)
      .map(reportDto -> (CombinedReportDefinitionRequestDto) reportDto)
      .findFirst();
    assertThat(combinedReport).isPresent();
    CombinedReportDataDto dataDto = combinedReport.get().getData();
    assertThat(dataDto.getReportIds()).hasSize(1);
    assertThat(dataDto.getReportIds().get(0)).isEqualTo(remainingSingleReportId);
  }

  @Test
  public void canEvaluateUnsavedCombinedReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap =
      result.getData();

    assertThat(resultMap).hasSize(2);
    List<MapResultEntryDto> flowNodeToCount = resultMap.get(singleReportId).getResult().getFirstMeasureData();
    assertThat(flowNodeToCount).hasSize(3);
    List<MapResultEntryDto> flowNodeToCount2 = resultMap.get(singleReportId2).getResult().getFirstMeasureData();
    assertThat(flowNodeToCount2).hasSize(3);
  }

  @Test
  public void evaluationResultContainsSingleResultMetaData() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<MapMeasureResponseDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<MapMeasureResponseDto>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(1);
    AuthorizedSingleReportEvaluationResponseDto<MapMeasureResponseDto, SingleProcessReportDefinitionRequestDto> mapResult =
      resultMap.get(singleReportId);
    assertThat(mapResult.getReportDefinition().getName()).isEqualTo(TEST_REPORT_NAME);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithSingleNumberReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId1 = createNewSingleNumberReport(engineDto);
    String singleReportId2 = createNewSingleNumberReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<Double> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(singleReportId1, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<Double>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(singleReportId1, singleReportId2);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithSingleMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId1 = createNewSingleMapReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(singleReportId1, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(singleReportId1, singleReportId2);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessDurationNumberReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleDurationNumberReport(engineDto);
    String singleReportId2 = createNewSingleDurationNumberReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<Double> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<Double>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(singleReportId, singleReportId2);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessDurationMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleDurationMapReport(engineDto);
    String singleReportId2 = createNewSingleDurationMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<Double> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<Double>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(singleReportId, singleReportId2);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessUserTaskTotalDurationMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String totalDurationReportId2 = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<Double> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(totalDurationReportId, totalDurationReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<Double>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(totalDurationReportId, totalDurationReportId2);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessUserTaskTotalDurationAndUserTaskIdleDurationMapReports() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleUserTaskTotalDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<Double> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(totalDurationReportId, idleDurationReportId));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<Double>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(totalDurationReportId, idleDurationReportId);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithProcessDurationMapReportAndUserTaskTotalDurationMapReport() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    String totalDurationReportId = createNewSingleDurationMapReport(engineDto);
    String idleDurationReportId = createNewSingleUserTaskIdleDurationMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<Double> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(totalDurationReportId, idleDurationReportId));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<Double>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.keySet()).contains(totalDurationReportId, idleDurationReportId);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithGroupedByProcessInstanceStartAndEndDateReports() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));

    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());

    String singleReportId1 = createNewSingleReportGroupByEndDate(engineDto, AggregateByDateUnit.DAY);
    String singleReportId2 = createNewSingleReportGroupByStartDate(engineDto, AggregateByDateUnit.DAY);

    importAllEngineEntitiesFromScratch();

    // when
    final CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(singleReportId1, singleReportId2));

    // then
    final Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap =
      result.getData();
    assertThat(resultMap).isNotNull();
    assertThat(resultMap.keySet()).contains(singleReportId1, singleReportId2);

    final ReportResultResponseDto<List<MapResultEntryDto>> result1 = resultMap.get(singleReportId1)
      .getResult();
    final List<MapResultEntryDto> resultData1 = result1.getFirstMeasureData();
    assertThat(resultData1)
      .isNotNull()
      .hasSize(1);

    final ReportResultResponseDto<List<MapResultEntryDto>> result2 = resultMap.get(singleReportId2)
      .getResult();
    final List<MapResultEntryDto> resultData2 = result2.getFirstMeasureData();
    assertThat(resultData2)
      .isNotNull()
      .hasSize(3);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithGroupedByUserTaskStartAndEndDateReports() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeFlowNodeStartDate(engineDto.getId(), USER_TASK_1, now.minusDays(2L));

    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto groupedByEndDateReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setReportDataType(USER_TASK_FREQ_GROUP_BY_USER_TASK_END_DATE)
      .build();
    String groupedByEndDateReportId = createNewSingleMapReport(groupedByEndDateReportData);
    final ProcessReportDataDto groupedByStartDateReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setReportDataType(USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE)
      .build();
    String groupedByStartDateReportId = createNewSingleMapReport(groupedByStartDateReportData);

    // when
    final CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(groupedByEndDateReportId, groupedByStartDateReportId));

    // then
    final Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap =
      result.getData();
    assertThat(resultMap).isNotNull();
    assertThat(resultMap.keySet()).contains(groupedByEndDateReportId, groupedByStartDateReportId);

    final ReportResultResponseDto<List<MapResultEntryDto>> result1 = resultMap.get(groupedByEndDateReportId)
      .getResult();
    final List<MapResultEntryDto> resultData1 = result1.getFirstMeasureData();
    assertThat(resultData1)
      .isNotNull()
      .hasSize(1);

    final ReportResultResponseDto<List<MapResultEntryDto>> result2 = resultMap.get(groupedByStartDateReportId)
      .getResult();
    final List<MapResultEntryDto> resultData2 = result2.getFirstMeasureData();
    assertThat(resultData2)
      .isNotNull()
      .hasSize(3);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithGroupedByFlowNodeStartAndEndDateReports() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeFlowNodeStartDate(engineDto.getId(), START_EVENT, now.minusDays(2L));

    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto groupedByEndDateReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setReportDataType(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_END_DATE)
      .build();
    String groupedByEndDateReportId = createNewSingleMapReport(groupedByEndDateReportData);
    final ProcessReportDataDto groupedByStartDateReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setReportDataType(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_START_DATE)
      .build();
    String groupedByStartDateReportId = createNewSingleMapReport(groupedByStartDateReportData);

    // when
    final CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(groupedByEndDateReportId, groupedByStartDateReportId));

    // then
    final Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap =
      result.getData();
    assertThat(resultMap).isNotNull();
    assertThat(resultMap.keySet()).contains(groupedByEndDateReportId, groupedByStartDateReportId);

    final ReportResultResponseDto<List<MapResultEntryDto>> result1 = resultMap.get(groupedByEndDateReportId)
      .getResult();
    final List<MapResultEntryDto> resultData1 = result1.getFirstMeasureData();
    assertThat(resultData1)
      .isNotNull()
      .hasSize(1);

    final ReportResultResponseDto<List<MapResultEntryDto>> result2 = resultMap.get(groupedByStartDateReportId)
      .getResult();
    final List<MapResultEntryDto> resultData2 = result2.getFirstMeasureData();
    assertThat(resultData2)
      .isNotNull()
      .hasSize(3);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithSingleNumberAndMapReport_firstWins() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleNumberReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<Double> result = reportClient.evaluateUnsavedCombined(
      createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<Double>> resultMap =
      result.getData();
    assertThat(resultMap).hasSize(1);
    assertThat(resultMap.keySet()).contains(singleReportId);
  }

  @Test
  public void canEvaluateUnsavedCombinedReportWithRawReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String singleReportId = createNewSingleRawReport(engineDto);
    String singleReportId2 = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap =
      result.getData();
    assertThat(resultMap)
      .hasSize(1)
      .containsKey(singleReportId2);
  }

  @Test
  public void cantEvaluateCombinedReportWithCombinedReport() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    String combinedReportId = createNewCombinedReport();
    String singleReportId2 = createNewSingleMapReport(engineDto);
    importAllEngineEntitiesFromScratch();

    // when
    Response response =
      evaluateUnsavedCombinedReportAndReturnResponse(createCombinedReportData(combinedReportId, singleReportId2));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void combinedReportWithHyperMapReportCanBeEvaluated() {
    // given
    ProcessInstanceEngineDto engineDto = deploySimpleServiceTaskProcessDefinition();
    createNewCombinedReport();
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_ASSIGNEE_BY_USER_TASK)
      .build();
    String singleReportId = createNewSingleMapReport(reportData);
    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap =
      result.getData();
    assertThat(resultMap).isEmpty();
  }

  private List<ProcessFilterDto<?>> buildStringVariableFilter(final String varName, final String varValue) {
    return ProcessFilterBuilder.filter()
      .variable()
      .name(varName)
      .stringType()
      .operator(IN)
      .values(Collections.singletonList(varValue))
      .add()
      .buildList();
  }

  private String createNewSingleMapReport(ProcessInstanceEngineDto engineDto) {
    return createNewSingleMapReportWithFilter(engineDto, null);
  }

  private String createNewSingleMapReportWithFilter(final ProcessInstanceEngineDto engineDto,
                                                    final AdditionalProcessReportEvaluationFilterDto filterDto) {
    final TemplatedProcessReportDataBuilder templatedProcessReportDataBuilder = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE);
    Optional.ofNullable(filterDto)
      .ifPresent(filters -> templatedProcessReportDataBuilder.setFilter(filters.getFilter()));
    return createNewSingleMapReport(templatedProcessReportDataBuilder.build());
  }

  private String createNewSingleDurationNumberReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .build();
    return createNewSingleNumberReport(durationReportData);
  }

  private String createNewSingleDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationMapReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
      .setVisualization(ProcessVisualization.TABLE)
      .build();
    return createNewSingleMapReport(durationMapReportData);
  }

  private String createNewSingleUserTaskTotalDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationMapReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setUserTaskDurationTime(UserTaskDurationTime.TOTAL)
      .setReportDataType(ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK)
      .setVisualization(ProcessVisualization.TABLE)
      .build();
    return createNewSingleMapReport(durationMapReportData);
  }

  private String createNewSingleUserTaskIdleDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto durationMapReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setUserTaskDurationTime(UserTaskDurationTime.IDLE)
      .setReportDataType(ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK)
      .build();
    return createNewSingleMapReport(durationMapReportData);
  }

  private String createNewSingleMapReport(ProcessReportDataDto data) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setName(TEST_REPORT_NAME);
    singleProcessReportDefinitionDto.setData(data);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleNumberReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    return createNewSingleNumberReport(countFlowNodeFrequencyGroupByFlowNode);
  }

  private String createNewSingleNumberReport(ProcessReportDataDto data) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(data);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleReportGroupByEndDate(ProcessInstanceEngineDto engineDto,
                                                     AggregateByDateUnit groupByDateUnit) {
    ProcessReportDataDto reportDataByEndDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(groupByDateUnit)
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_END_DATE)
      .build();
    return createNewSingleMapReport(reportDataByEndDate);
  }

  private String createNewSingleReportGroupByStartDate(ProcessInstanceEngineDto engineDto,
                                                       AggregateByDateUnit groupByDateUnit) {
    ProcessReportDataDto reportDataByStartDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(groupByDateUnit)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    return createNewSingleMapReport(reportDataByStartDate);
  }


  private String createNewSingleRawReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewCombinedReport(String... singleReportIds) {
    CombinedReportDefinitionRequestDto report = new CombinedReportDefinitionRequestDto();
    report.setData(createCombinedReportData(singleReportIds));
    return createNewCombinedReport(report);
  }

  private String createNewCombinedReport(CombinedReportDefinitionRequestDto report) {
    String reportId = createNewCombinedReportInCollection(null);
    updateReport(reportId, report);
    return reportId;
  }

  private String createNewCombinedReportInCollection(String collectionId) {
    CombinedReportDefinitionRequestDto combinedReportDefinitionDto = new CombinedReportDefinitionRequestDto();
    combinedReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private ProcessInstanceEngineDto deploySimpleServiceTaskProcessDefinition() {
    return engineIntegrationExtension.deployAndStartProcess(getSingleServiceTaskProcess());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcess() {
    return deployAndStartSimpleUserTaskProcessWithVariables(Collections.emptyMap());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcessWithVariables(Map<String, Object> variables) {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      BpmnModels.getSingleUserTaskDiagram(),
      variables
    );
  }

  private void deleteReport(String reportId) {
    deleteReport(reportId, null);
  }

  private void deleteReport(String reportId, Boolean force) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  private String createNewSingleReport(SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private void updateReport(String id, SingleProcessReportDefinitionRequestDto updatedReport, Boolean force) {
    Response response = getUpdateSingleProcessReportResponse(id, updatedReport, force);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  private void updateReport(String id, CombinedReportDefinitionRequestDto updatedReport) {
    updateReport(id, updatedReport, null);
  }

  private void updateReport(String id, CombinedReportDefinitionRequestDto updatedReport, Boolean force) {
    Response response = getUpdateCombinedProcessReportResponse(id, updatedReport, force);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  private Response getUpdateSingleProcessReportResponse(String id,
                                                        SingleProcessReportDefinitionRequestDto updatedReport,
                                                        Boolean force) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport, force)
      .execute();
  }

  private Response getUpdateCombinedProcessReportResponse(String id, CombinedReportDefinitionRequestDto updatedReport,
                                                          Boolean force) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(id, updatedReport, force)
      .execute();
  }

  private Response evaluateUnsavedCombinedReportAndReturnResponse(CombinedReportDataDto reportDataDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
      .execute();
  }

  private List<ReportDefinitionDto> getAllPrivateReports() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAllPrivateReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  private Response addSingleReportToCombinedReport(final String combinedReportId, final String reportId) {
    final CombinedReportDefinitionRequestDto combinedReportData = new CombinedReportDefinitionRequestDto();
    combinedReportData.getData().getReports().add(new CombinedReportItemDto(reportId, "red"));
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportData)
      .execute();
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  protected static class CombinedReportUpdateData {
    String singleReportId;
    String collectionId;
  }
}
