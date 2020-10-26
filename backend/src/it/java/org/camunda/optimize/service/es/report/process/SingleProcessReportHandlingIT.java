/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value.HeatmapTargetValueEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class SingleProcessReportHandlingIT extends AbstractIT {

  private static final String FOO_PROCESS_DEFINITION_KEY = "fooProcessDefinitionKey";
  private static final String FOO_PROCESS_DEFINITION_VERSION = "1";

  @AfterEach
  public void cleanUp() {
    LocalDateUtil.reset();
  }

  @Test
  public void reportIsWrittenToElasticsearch() throws IOException {
    // given
    String id = reportClient.createEmptySingleProcessReport();

    // when
    GetRequest getRequest = new GetRequest(SINGLE_PROCESS_REPORT_INDEX_NAME).id(id);
    GetResponse getResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .get(getRequest, RequestOptions.DEFAULT);

    // then
    assertThat(getResponse.isExists()).isTrue();
    SingleProcessReportDefinitionRequestDto definitionDto = elasticSearchIntegrationTestExtension.getObjectMapper()
      .readValue(getResponse.getSourceAsString(), SingleProcessReportDefinitionRequestDto.class);
    assertThat(definitionDto.getData()).isNotNull();
    ProcessReportDataDto data = definitionDto.getData();
    assertThat(data.getFilter()).isNotNull();
    assertThat(data.getConfiguration()).isNotNull();
    assertThat(data.getConfiguration()).isEqualTo(new SingleReportConfigurationDto());
    assertThat(data.getConfiguration().getColor()).isEqualTo(ReportConstants.DEFAULT_CONFIGURATION_COLOR);
  }

  @Test
  public void writeAndThenReadGivesTheSameResult() {
    // given
    String id = reportClient.createEmptySingleProcessReport();

    // when
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports).isNotNull();
    assertThat(reports.size()).isEqualTo(1);
    assertThat(reports.get(0).getId()).isEqualTo(id);
  }

  @Test
  public void createAndGetSeveralReports() {
    // given
    String id = reportClient.createEmptySingleProcessReport();
    String id2 = reportClient.createEmptySingleProcessReport();
    Set<String> ids = new HashSet<>();
    ids.add(id);
    ids.add(id2);

    // when
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports).isNotNull();
    assertThat(reports.size()).isEqualTo(2);
    String reportId1 = reports.get(0).getId();
    String reportId2 = reports.get(1).getId();
    assertThat(ids.contains(reportId1)).isTrue();
    ids.remove(reportId1);
    assertThat(ids.contains(reportId2)).isTrue();
  }

  @Test
  public void noReportAvailableReturnsEmptyList() {

    // when
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports).isNotNull();
    assertThat(reports.isEmpty()).isTrue();
  }

  @Test
  public void updateProcessReport() {
    // given
    final String shouldNotBeUpdatedString = "shouldNotBeUpdated";
    String id = reportClient.createEmptySingleProcessReport();
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey("procdef");
    reportData.setProcessDefinitionVersion("123");
    reportData.setFilter(Collections.emptyList());
    SingleReportConfigurationDto configuration = new SingleReportConfigurationDto();
    configuration.setYLabel("fooYLabel");
    reportData.setConfiguration(configuration);
    ProcessPartDto processPartDto = new ProcessPartDto();
    processPartDto.setStart("start123");
    processPartDto.setEnd("end123");
    reportData.getConfiguration().setProcessPart(processPartDto);
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setId(shouldNotBeUpdatedString);
    report.setLastModifier("shouldNotBeUpdatedManually");
    report.setName("MyReport");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(shouldBeIgnoredDate);
    report.setLastModified(shouldBeIgnoredDate);
    report.setOwner(shouldNotBeUpdatedString);

    // when
    reportClient.updateSingleProcessReport(id, report);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports.size()).isEqualTo(1);
    SingleProcessReportDefinitionRequestDto newReport = (SingleProcessReportDefinitionRequestDto) reports.get(0);
    assertThat(newReport.getData().getProcessDefinitionKey()).isEqualTo("procdef");
    assertThat(newReport.getData().getDefinitionVersions()).containsExactly("123");
    assertThat(newReport.getData().getConfiguration().getYLabel()).isEqualTo("fooYLabel");
    assertThat(newReport.getData().getConfiguration().getProcessPart()).isNotEmpty();
    assertThat(newReport.getData().getConfiguration().getProcessPart().get().getStart()).isEqualTo("start123");
    assertThat(newReport.getData().getConfiguration().getProcessPart().get().getEnd()).isEqualTo("end123");
    assertThat(newReport.getId()).isEqualTo(id);
    assertThat(newReport.getCreated()).isNotEqualTo(shouldBeIgnoredDate);
    assertThat(newReport.getLastModified()).isNotEqualTo(shouldBeIgnoredDate);
    assertThat(newReport.getName()).isEqualTo("MyReport");
    assertThat(newReport.getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  @Test
  public void updateProcessReportRemoveHeatMapTargetValue() {
    // given
    final String id = reportClient.createEmptySingleProcessReport();
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey("procdef");
    reportData.setProcessDefinitionVersion("123");
    reportData.setFilter(Collections.emptyList());
    final SingleReportConfigurationDto configuration = new SingleReportConfigurationDto();
    configuration.getHeatmapTargetValue().getValues()
      .put("flowNodeId", new HeatmapTargetValueEntryDto(TargetValueUnit.DAYS, "55"));
    reportData.setConfiguration(configuration);

    final SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setId("shouldNotBeUpdated");
    report.setLastModifier("shouldNotBeUpdatedManually");
    report.setName("MyReport");
    report.setOwner("NewOwner");
    reportClient.updateSingleProcessReport(id, report);

    // when
    configuration.getHeatmapTargetValue().setValues(new HashMap<>());
    reportClient.updateSingleProcessReport(id, report);
    final List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports.size()).isEqualTo(1);
    SingleProcessReportDefinitionRequestDto newReport = (SingleProcessReportDefinitionRequestDto) reports.get(0);
    assertThat(newReport.getData().getConfiguration().getHeatmapTargetValue().getValues()).isEmpty();
  }

  @Test
  public void updateReportWithoutPDInformation() {
    // given
    String id = reportClient.createEmptySingleProcessReport();
    SingleProcessReportDefinitionRequestDto updatedReport = new SingleProcessReportDefinitionRequestDto();
    updatedReport.setData(new ProcessReportDataDto());

    //when
    Response updateReportResponse = reportClient.updateSingleProcessReport(id, updatedReport);

    //then
    assertThat(updateReportResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    //when
    ProcessReportDataDto data = new ProcessReportDataDto();
    data.setProcessDefinitionVersion("BLAH");
    updatedReport.setData(data);
    updateReportResponse = reportClient.updateSingleProcessReport(id, updatedReport);

    //then
    assertThat(updateReportResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    //when
    data = new ProcessReportDataDto();
    data.setProcessDefinitionKey("BLAH");
    updatedReport.setData(data);
    updateReportResponse = reportClient.updateSingleProcessReport(id, updatedReport);

    //then
    assertThat(updateReportResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    //when
    data = new ProcessReportDataDto();
    data.setProcessDefinitionKey("BLAH");
    data.setProcessDefinitionVersion("BLAH");
    updatedReport.setData(data);
    updateReportResponse = reportClient.updateSingleProcessReport(id, updatedReport);

    //then
    assertThat(updateReportResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void updateReportWithFilters() {
    // given
    String id = reportClient.createEmptySingleProcessReport();
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey("procdef");
    reportData.setProcessDefinitionVersion("123");

    reportData.getFilter()
      .addAll(ProcessFilterBuilder.filter().fixedStartDate().start(null).end(null).add().buildList());
    reportData.getFilter().addAll(createVariableFilter());
    reportData.getFilter().addAll(createExecutedFlowNodeFilter());
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setId("shouldNotBeUpdated");
    report.setLastModifier("shouldNotBeUpdatedManually");
    report.setName("MyReport");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(shouldBeIgnoredDate);
    report.setLastModified(shouldBeIgnoredDate);
    report.setOwner("NewOwner");

    // when
    reportClient.updateSingleProcessReport(id, report);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports.size()).isEqualTo(1);
    SingleProcessReportDefinitionRequestDto newReport =
      (SingleProcessReportDefinitionRequestDto) reports.get(0);
    assertThat(newReport.getData()).isNotNull();
    reportData = newReport.getData();
    assertThat(reportData.getFilter().size()).isEqualTo(3);
  }

  private List<ProcessFilterDto<?>> createVariableFilter() {
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(new BooleanVariableFilterDataDto("foo", Collections.singletonList(true)));
    return Collections.singletonList(variableFilterDto);
  }

  private List<ProcessFilterDto<?>> createExecutedFlowNodeFilter() {
    List<ProcessFilterDto<?>> flowNodeFilter = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id("task1")
      .add()
      .buildList();
    return new ArrayList<>(flowNodeFilter);
  }

  @Test
  public void doNotUpdateNullFieldsInReport() {
    // given
    String id = reportClient.createEmptySingleProcessReport();
    SingleProcessReportDefinitionRequestDto report = constructSingleProcessReportWithFakePD();

    // when
    reportClient.updateSingleProcessReport(id, report);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports.size()).isEqualTo(1);
    ReportDefinitionDto newDashboard = reports.get(0);
    assertThat(newDashboard.getId()).isEqualTo(id);
    assertThat(newDashboard.getCreated()).isNotNull();
    assertThat(newDashboard.getLastModified()).isNotNull();
    assertThat(newDashboard.getLastModifier()).isNotNull();
    assertThat(newDashboard.getName()).isNotNull();
    assertThat(newDashboard.getOwner()).isNotNull();
  }

  @Test
  public void reportEvaluationReturnsMetaData() {
    // given
    String reportId = reportClient.createEmptySingleProcessReport();
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(FOO_PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion(FOO_PROCESS_DEFINITION_VERSION)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setName("name");
    OffsetDateTime now = OffsetDateTime.now();
    reportClient.updateSingleProcessReport(reportId, report);

    // when
    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result =
      reportClient.evaluateRawReportById(reportId);

    // then
    final SingleProcessReportDefinitionRequestDto reportDefinition = result.getReportDefinition();
    assertThat(reportDefinition.getId()).isEqualTo(reportId);
    assertThat(reportDefinition.getName()).isEqualTo("name");
    assertThat(reportDefinition.getOwner()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(reportDefinition.getCreated().truncatedTo(ChronoUnit.DAYS)).isEqualTo(now.truncatedTo(ChronoUnit.DAYS));
    assertThat(reportDefinition.getLastModifier()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(reportDefinition.getLastModified()
                 .truncatedTo(ChronoUnit.DAYS)).isEqualTo(now.truncatedTo(ChronoUnit.DAYS));
  }

  @Test
  public void evaluateReportWithoutVisualization() {
    // given
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey("foo")
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .build();
    reportData.setVisualization(null);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  private SingleProcessReportDefinitionRequestDto constructSingleProcessReportWithFakePD() {
    SingleProcessReportDefinitionRequestDto reportDefinitionDto = new SingleProcessReportDefinitionRequestDto();
    ProcessReportDataDto data = new ProcessReportDataDto();
    data.setProcessDefinitionVersion("FAKE");
    data.setProcessDefinitionKey("FAKE");
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  private List<ReportDefinitionDto> getAllPrivateReports() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAllPrivateReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

}
