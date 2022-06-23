/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.rest.ProcessRawDataCsvExportRequestDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ExportRestServiceIT extends AbstractIT {

  private static final String COLUMN_PROCESS_INSTANCE_ID = ProcessInstanceDto.Fields.processInstanceId;
  private static final String SAMPLE_PROCESS_DEFINITION_KEY = "some";

  @Test
  public void exportWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildCsvExportRequest("fake_id", "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.TEMPORARY_REDIRECT.getStatusCode());
    assertThat(response.getLocation().getPath()).isEqualTo("/");
  }

  @Test
  public void exportExistingRawProcessReportWithoutFilename() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultValidRawProcessReportDefinition(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void exportExistingRawProcessReport() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultValidRawProcessReportDefinition(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(getResponseContentAsString(response)).hasSizeGreaterThan(0);
  }

  @Test
  public void exportExistingRawDecisionReport() throws IOException {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      engineIntegrationExtension.deployAndStartDecisionDefinition();
    String reportId = createAndStoreDefaultValidRawDecisionReportDefinition(
      decisionDefinitionEngineDto.getKey(),
      String.valueOf(decisionDefinitionEngineDto.getVersion())
    );
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
    assertThat(result).hasSizeGreaterThan(0);
  }

  @Test
  public void exportExistingInvalidReport() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultInvalidReportDefinition(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void exportNotExistingReport() {
    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildCsvExportRequest("UFUK", "IGDE.csv")
        .execute();
    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("getInvalidDynamicRawProcessExportRequests")
  public void exportDynamicRawProcessReport_rejectInvalidRequests(final ProcessRawDataCsvExportRequestDto invalidRequest) {
    // given
    deployAndStartSimpleProcess();

    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDynamicRawProcessCsvExportRequest(invalidRequest, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private static Stream<ProcessRawDataCsvExportRequestDto> getInvalidDynamicRawProcessExportRequests() {
    return Stream.of(
      ProcessRawDataCsvExportRequestDto.builder()
        .processDefinitionKey(null)
        .processDefinitionVersions(Collections.singletonList(ReportConstants.ALL_VERSIONS))
        .includedColumns(Collections.singletonList(COLUMN_PROCESS_INSTANCE_ID))
        .build(),
      ProcessRawDataCsvExportRequestDto.builder()
        .processDefinitionKey(SAMPLE_PROCESS_DEFINITION_KEY)
        .processDefinitionVersions(null)
        .includedColumns(Collections.singletonList(COLUMN_PROCESS_INSTANCE_ID))
        .build(),
      ProcessRawDataCsvExportRequestDto.builder()
        .processDefinitionKey(SAMPLE_PROCESS_DEFINITION_KEY)
        .processDefinitionVersions(Collections.emptyList())
        .includedColumns(Collections.singletonList(COLUMN_PROCESS_INSTANCE_ID))
        .build(),
      ProcessRawDataCsvExportRequestDto.builder()
        .processDefinitionKey(SAMPLE_PROCESS_DEFINITION_KEY)
        .processDefinitionVersions(Collections.singletonList(ReportConstants.ALL_VERSIONS))
        .includedColumns(null)
        .build(),
      ProcessRawDataCsvExportRequestDto.builder()
        .processDefinitionKey(SAMPLE_PROCESS_DEFINITION_KEY)
        .processDefinitionVersions(Collections.singletonList(ReportConstants.ALL_VERSIONS))
        .includedColumns(Collections.emptyList())
        .build(),
      ProcessRawDataCsvExportRequestDto.builder()
        .processDefinitionKey(SAMPLE_PROCESS_DEFINITION_KEY)
        .processDefinitionVersions(Collections.singletonList(ReportConstants.ALL_VERSIONS))
        .includedColumns(Collections.singletonList(COLUMN_PROCESS_INSTANCE_ID))
        .tenantIds(null)
        .build(),
      ProcessRawDataCsvExportRequestDto.builder()
        .processDefinitionKey(SAMPLE_PROCESS_DEFINITION_KEY)
        .processDefinitionVersions(Collections.singletonList(ReportConstants.ALL_VERSIONS))
        .includedColumns(Collections.singletonList(COLUMN_PROCESS_INSTANCE_ID))
        .filter(null)
        .build(),
      ProcessRawDataCsvExportRequestDto.builder()
        .processDefinitionKey(SAMPLE_PROCESS_DEFINITION_KEY)
        .processDefinitionVersions(Collections.singletonList(ReportConstants.ALL_VERSIONS))
        .includedColumns(null)
        .build(),
      ProcessRawDataCsvExportRequestDto.builder()
        .processDefinitionKey(SAMPLE_PROCESS_DEFINITION_KEY)
        .processDefinitionVersions(Collections.singletonList(ReportConstants.ALL_VERSIONS))
        .includedColumns(Collections.emptyList())
        .build()
    );
  }

  @Test
  public void exportDynamicRawProcessReport_withJustProcessInstanceId() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    importAllEngineEntitiesFromScratch();

    final ProcessRawDataCsvExportRequestDto exportRequestDto = ProcessRawDataCsvExportRequestDto.builder()
      .processDefinitionKey(processInstance.getProcessDefinitionKey())
      .processDefinitionVersions(Collections.singletonList(processInstance.getProcessDefinitionVersion()))
      .includedColumns(Collections.singletonList(COLUMN_PROCESS_INSTANCE_ID))
      .build();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDynamicRawProcessCsvExportRequest(exportRequestDto, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String[] csvString = getResponseContentAsString(response).split("\\r\\n");
    assertThat(csvString)
      .containsExactly(escapeString(COLUMN_PROCESS_INSTANCE_ID), escapeString(processInstance.getId()));
  }

  @Test
  public void exportDynamicRawProcessReport_withJustProcessInstanceIdSpecificVersion() {
    // given
    deployAndStartSimpleProcess();
    ProcessInstanceEngineDto processInstanceVersion2 = deployAndStartSimpleProcess();

    importAllEngineEntitiesFromScratch();

    final ProcessRawDataCsvExportRequestDto exportRequestDto = ProcessRawDataCsvExportRequestDto.builder()
      .processDefinitionKey(processInstanceVersion2.getProcessDefinitionKey())
      .processDefinitionVersions(Collections.singletonList(processInstanceVersion2.getProcessDefinitionVersion()))
      .includedColumns(Collections.singletonList(COLUMN_PROCESS_INSTANCE_ID))
      .build();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDynamicRawProcessCsvExportRequest(exportRequestDto, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String[] csvString = getResponseContentAsString(response).split("\\r\\n");
    assertThat(csvString)
      .containsExactly(escapeString(COLUMN_PROCESS_INSTANCE_ID), escapeString(processInstanceVersion2.getId()));
  }

  @Test
  public void exportDynamicRawProcessReport_withJustProcessInstanceIdAllVersions() {
    // given
    ProcessInstanceEngineDto processInstanceVersion1 = deployAndStartSimpleProcess();
    ProcessInstanceEngineDto processInstanceVersion2 = deployAndStartSimpleProcess();

    importAllEngineEntitiesFromScratch();

    final ProcessRawDataCsvExportRequestDto exportRequestDto = ProcessRawDataCsvExportRequestDto.builder()
      .processDefinitionKey(processInstanceVersion2.getProcessDefinitionKey())
      .processDefinitionVersions(Collections.singletonList(ReportConstants.ALL_VERSIONS))
      .includedColumns(Collections.singletonList(COLUMN_PROCESS_INSTANCE_ID))
      .build();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDynamicRawProcessCsvExportRequest(exportRequestDto, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String[] csvString = getResponseContentAsString(response).split("\\r\\n");
    assertThat(csvString)
      .containsExactly(
        escapeString(COLUMN_PROCESS_INSTANCE_ID),
        escapeString(processInstanceVersion2.getId()),
        escapeString(processInstanceVersion1.getId())
      );
  }

  @Test
  public void exportDynamicRawProcessReport_withJustProcessInstanceIdSpecificTenant() {
    // given
    final String tenantId1 = "tenant1";
    engineIntegrationExtension.createTenant(tenantId1);
    ProcessInstanceEngineDto processInstanceTenant1 = deployAndStartSimpleProcess(tenantId1);
    final String tenantId2 = "tenant2";
    engineIntegrationExtension.createTenant(tenantId2);
    ProcessInstanceEngineDto processInstanceTenant2 = deployAndStartSimpleProcess(tenantId2);

    importAllEngineEntitiesFromScratch();

    final ProcessRawDataCsvExportRequestDto exportRequestDto = ProcessRawDataCsvExportRequestDto.builder()
      .processDefinitionKey(processInstanceTenant2.getProcessDefinitionKey())
      .processDefinitionVersions(Collections.singletonList(ReportConstants.ALL_VERSIONS))
      .tenantIds(Collections.singletonList(tenantId1))
      .includedColumns(Collections.singletonList(COLUMN_PROCESS_INSTANCE_ID))
      .build();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDynamicRawProcessCsvExportRequest(exportRequestDto, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String[] csvString = getResponseContentAsString(response).split("\\r\\n");
    assertThat(csvString)
      .containsExactly(escapeString(COLUMN_PROCESS_INSTANCE_ID), escapeString(processInstanceTenant1.getId()));
  }

  @Test
  @SneakyThrows
  public void exportDynamicRawProcessReport_withJustProcessInstanceIdWithFilter() {
    // given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcess();
    final ProcessInstanceEngineDto processInstance2 = engineIntegrationExtension
      .startProcessInstance(processInstance1.getDefinitionId());

    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime modifiedStartDate = LocalDateUtil.getCurrentDateTime().minusSeconds(20);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance1.getId(), modifiedStartDate);

    importAllEngineEntitiesFromScratch();

    final ProcessRawDataCsvExportRequestDto exportRequestDto = ProcessRawDataCsvExportRequestDto.builder()
      .processDefinitionKey(processInstance1.getProcessDefinitionKey())
      .processDefinitionVersions(Collections.singletonList(ReportConstants.ALL_VERSIONS))
      .includedColumns(Collections.singletonList(COLUMN_PROCESS_INSTANCE_ID))
      .filter(
        ProcessFilterBuilder.filter()
          .rollingInstanceStartDate().start(10L, DateUnit.SECONDS).add()
          .buildList()
      )
      .build();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDynamicRawProcessCsvExportRequest(exportRequestDto, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String[] csvString = getResponseContentAsString(response).split("\\r\\n");
    assertThat(csvString)
      .containsExactly(escapeString(COLUMN_PROCESS_INSTANCE_ID), escapeString(processInstance2.getId()));
  }

  private static String escapeString(final String string) {
    return "\"" + string + "\"";
  }

  private String createAndStoreDefaultValidRawProcessReportDefinition(String processDefinitionKey,
                                                                      String processDefinitionVersion) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    return createAndStoreDefaultProcessReportDefinition(reportData);
  }

  private String createAndStoreDefaultValidRawDecisionReportDefinition(String decisionDefinitionKey,
                                                                       String decisionDefinitionVersion) {
    DecisionReportDataDto reportData = DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(decisionDefinitionKey)
      .setDecisionDefinitionVersion(decisionDefinitionVersion)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
    return createAndStoreDefaultDecisionReportDefinition(reportData);
  }

  private String createAndStoreDefaultInvalidReportDefinition(String processDefinitionKey,
                                                              String processDefinitionVersion) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    reportData.setGroupBy(new NoneGroupByDto());
    reportData.setVisualization(ProcessVisualization.NUMBER);
    return createAndStoreDefaultProcessReportDefinition(reportData);
  }

  private String createAndStoreDefaultProcessReportDefinition(ProcessReportDataDto reportData) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportData);
    singleProcessReportDefinitionDto.setName("something");
    return createNewProcessReport(singleProcessReportDefinitionDto);
  }

  private String createAndStoreDefaultDecisionReportDefinition(DecisionReportDataDto decisionReportDataDto) {
    SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
      new SingleDecisionReportDefinitionRequestDto();
    singleDecisionReportDefinitionDto.setData(decisionReportDataDto);
    singleDecisionReportDefinitionDto.setLastModifier("something");
    singleDecisionReportDefinitionDto.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleDecisionReportDefinitionDto.setCreated(someDate);
    singleDecisionReportDefinitionDto.setLastModified(someDate);
    singleDecisionReportDefinitionDto.setOwner("something");
    return createSingleDecisionReport(singleDecisionReportDefinitionDto);
  }

  private String createSingleDecisionReport(SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private String createNewProcessReport(SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcess(null);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess(String tenantId) {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>(), tenantId);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables,
                                                                            String tenantId) {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(getSimpleBpmnDiagram(), variables, tenantId);
  }
}