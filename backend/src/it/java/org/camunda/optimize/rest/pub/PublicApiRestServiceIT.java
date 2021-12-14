/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.pub;

import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableRequestDto.QUERY_LIMIT_PARAM;
import static org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableRequestDto.QUERY_SCROLL_ID_PARAM;
import static org.camunda.optimize.rest.PublicApiRestService.QUERY_PARAMETER_ACCESS_TOKEN;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class PublicApiRestServiceIT extends AbstractIT {

  private String generateValidReport(int numberOfInstances) {
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultValidRawProcessReportDefinition(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );
    //-1 because the call deployAndStartSimpleProcess already creates one process instance
    for (int i = 0; i < numberOfInstances - 1; i++) {
      engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    }
    importAllEngineEntitiesFromScratch();
    return reportId;
  }

  @Test
  public void exportReportResultWithoutAuthorization() {
    //given
    //make sure a token is generated but don't use it
    getAccessToken();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildPublicExportJsonReportResultRequest("fake_id")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void exportReportDefinitionWithoutAuthorization() {
    // given
    getAccessToken();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildPublicExportJsonReportDefinitionRequest(Collections.singletonList("fake_id"), null)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void deleteReportDefinitionWithoutAuthorization() {
    // given
    getAccessToken();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildPublicDeleteReportRequest("fake_id", null)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void exportDashboardDefinitionWithoutAuthorization() {
    // given
    getAccessToken();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildPublicExportJsonDashboardDefinitionRequest(Collections.singletonList("fake_id"), null)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void failGracefullyWhenNoSecretIsConfigured() {

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildPublicExportJsonReportResultRequest("fake_id")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void exportExistingRawProcessReportResultInOnePage() {
    // given
    int numberOfInstances = 10;
    String reportId = generateValidReport(numberOfInstances);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, getAccessToken())
      .addSingleQueryParam(QUERY_LIMIT_PARAM, numberOfInstances)
      .buildPublicExportJsonReportResultRequest(reportId)
      .withoutAuthentication()
      .execute();
    PaginatedDataExportDto data = response.readEntity(PaginatedDataExportDto.class);

    // then
    assertThat(data.getNumberOfRecordsInResponse()).isEqualTo(numberOfInstances);
    assertThat((long) data.getNumberOfRecordsInResponse()).isEqualTo(data.getTotalNumberOfRecords());
    assertThat(data.getSearchRequestId()).isNotBlank();
    assertThat(response.getStatus())
      .isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void exportExistingRawProcessReportResultInSeveralPages() {
    // given
    int numberOfInstances = 11;
    int limit = numberOfInstances / 2;
    String reportId = generateValidReport(numberOfInstances);

    // when
    Response responsePage1 = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, getAccessToken())
      .addSingleQueryParam(QUERY_LIMIT_PARAM, limit)
      .buildPublicExportJsonReportResultRequest(reportId)
      .withoutAuthentication()
      .execute();
    PaginatedDataExportDto dataPage1 =
      responsePage1.readEntity(PaginatedDataExportDto.class);

    Response responsePage2 = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, getAccessToken())
      .addSingleQueryParam(QUERY_SCROLL_ID_PARAM, dataPage1.getSearchRequestId())
      .buildPublicExportJsonReportResultRequest(reportId)
      .withoutAuthentication()
      .execute();
    PaginatedDataExportDto dataPage2 =
      responsePage2.readEntity(PaginatedDataExportDto.class);

    Response responsePage3 = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, getAccessToken())
      .addSingleQueryParam(QUERY_SCROLL_ID_PARAM, dataPage2.getSearchRequestId())
      .buildPublicExportJsonReportResultRequest(reportId)
      .withoutAuthentication()
      .execute();
    PaginatedDataExportDto dataPage3 =
      responsePage3.readEntity(PaginatedDataExportDto.class);

    // then
    assertThat(dataPage1.getTotalNumberOfRecords())
      .isEqualTo(dataPage2.getTotalNumberOfRecords())
      .isEqualTo(dataPage3.getTotalNumberOfRecords())
      .isEqualTo(numberOfInstances);
    assertThat(dataPage1.getNumberOfRecordsInResponse())
      .isEqualTo(dataPage2.getNumberOfRecordsInResponse())
      .isEqualTo(limit);
    assertThat(dataPage3.getNumberOfRecordsInResponse())
      .isLessThan(limit);

    assertThat(dataPage1.getSearchRequestId()).isNotBlank();
    assertThat(dataPage2.getSearchRequestId()).isNotBlank();
    assertThat(dataPage3.getSearchRequestId()).isNotBlank();
    assertThat(responsePage1.getStatus())
      .isEqualTo(responsePage2.getStatus())
      .isEqualTo(responsePage3.getStatus())
      .isEqualTo(Response.Status.OK.getStatusCode());

    //Make sure the data in the pages are different
    assertThat(extractFirstProcessInstanceId(dataPage1.getDataAs(List.class))).
      isNotEqualTo(extractFirstProcessInstanceId(dataPage2.getDataAs(List.class)));
    assertThat(extractFirstProcessInstanceId(dataPage1.getDataAs(List.class)))
      .isNotEqualTo(extractFirstProcessInstanceId(dataPage3.getDataAs(List.class)));
    assertThat(extractFirstProcessInstanceId(dataPage2.getDataAs(List.class)))
      .isNotEqualTo(extractFirstProcessInstanceId(dataPage3.getDataAs(List.class)));
  }

  private String extractFirstProcessInstanceId(List<?> data) {
    Object firstData = data.get(0);
    if (firstData instanceof Map) {
      Map<String, String> firstDataPair = (Map<String, String>) firstData;
      return firstDataPair.get("processInstanceId");
    }
    return "";
  }

  @Test
  public void exportExistingInvalidReportResult() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultInvalidReportDefinition(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, getAccessToken())
      .buildPublicExportJsonReportResultRequest(reportId)
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void exportNotExistingReportResult() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, getAccessToken())
      .buildPublicExportJsonReportResultRequest("IWishIExisted_ButIDont")
      .withoutAuthentication()
      .execute();
    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void paginatingWithInvalidScrollId() {
    // given
    int numberOfInstances = 2;
    String reportId = generateValidReport(numberOfInstances);

    // when
    // Providing a non-existing scrollId
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, getAccessToken())
      .addSingleQueryParam(QUERY_LIMIT_PARAM, numberOfInstances)
      .addSingleQueryParam(QUERY_SCROLL_ID_PARAM, "NoSoupForYou!")
      .withoutAuthentication()
      .buildPublicExportJsonReportResultRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus())
      .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  @SneakyThrows
  public void paginatingWithExpiredScrollId() {

    // given
    int numberOfInstances = 3;
    int limit = numberOfInstances / 2;
    String reportId = generateValidReport(numberOfInstances);

    // when
    Response responsePage1 = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, getAccessToken())
      .addSingleQueryParam(QUERY_LIMIT_PARAM, limit)
      .buildPublicExportJsonReportResultRequest(reportId)
      .withoutAuthentication()
      .execute();
    PaginatedDataExportDto dataPage1 =
      responsePage1.readEntity(PaginatedDataExportDto.class);

    ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
    clearScrollRequest.addScrollId(dataPage1.getSearchRequestId());
    ClearScrollResponse clearScrollResponse = embeddedOptimizeExtension.getOptimizeElasticClient()
      .clearScroll(clearScrollRequest);
    boolean succeeded = clearScrollResponse.isSucceeded();

    Response responsePage2 = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, getAccessToken())
      .addSingleQueryParam(QUERY_SCROLL_ID_PARAM, dataPage1.getSearchRequestId())
      .buildPublicExportJsonReportResultRequest(reportId)
      .withoutAuthentication()
      .execute();

    // then
    assert (succeeded);
    assertThat(responsePage1.getStatus())
      .isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(dataPage1.getSearchRequestId()).isNotBlank();
    assertThat(responsePage2.getStatus())
      .isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void paginateWhenResultsExhausted() {
    // given
    int numberOfInstances = 10;
    String reportId = generateValidReport(numberOfInstances);

    // when
    // This retrieves all results, since limit is 3 times as big as number of instances
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, getAccessToken())
      .addSingleQueryParam(QUERY_LIMIT_PARAM, numberOfInstances * 3)
      .buildPublicExportJsonReportResultRequest(reportId)
      .withoutAuthentication()
      .execute();
    PaginatedDataExportDto data = response.readEntity(PaginatedDataExportDto.class);

    //Now there are no results left, but I try to get them anyway
    Response responsePage2 = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, getAccessToken())
      .addSingleQueryParam(QUERY_SCROLL_ID_PARAM, data.getSearchRequestId())
      .buildPublicExportJsonReportResultRequest(reportId)
      .withoutAuthentication()
      .execute();
    PaginatedDataExportDto dataPage2 =
      responsePage2.readEntity(PaginatedDataExportDto.class);

    // then
    assertThat(data.getNumberOfRecordsInResponse()).isEqualTo(numberOfInstances);
    assertThat((long) data.getNumberOfRecordsInResponse()).isEqualTo(data.getTotalNumberOfRecords());
    assertThat(data.getSearchRequestId()).isNotBlank();
    assertThat(response.getStatus())
      .isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(dataPage2.getTotalNumberOfRecords()).isEqualTo(numberOfInstances);
    assertThat(dataPage2.getNumberOfRecordsInResponse()).isZero();
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

  private String createNewProcessReport(SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(getSimpleBpmnDiagram(), new HashMap<>(), null);
  }

  private String getAccessToken() {
    return
      Optional.ofNullable(
        embeddedOptimizeExtension.getConfigurationService().getJsonExportConfiguration().getAccessToken())
        .orElseGet(() -> {
          String randomToken = "1_2_Polizei";
          embeddedOptimizeExtension.getConfigurationService().getJsonExportConfiguration().setAccessToken(
            randomToken);
          return randomToken;
        });
  }
}