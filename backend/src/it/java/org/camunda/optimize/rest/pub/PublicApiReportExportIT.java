/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.pub;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableRequestDto.QUERY_LIMIT_PARAM;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.SuppressionConstants.SAME_PARAM_VALUE;

public class PublicApiReportExportIT extends AbstractIT {

  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_2 = "userTask2";
  private static final String SECOND_USER = "secondUser";
  private static final String SECOND_USERS_PASSWORD = "fooPassword";
  private static final String USER_TASK_A = "userTaskA";
  private static final String USER_TASK_B = "userTaskB";
  private static final String START = "aStart";
  private static final String END = "anEnd";

  @Test
  public void rawDataReportExportResultInOnePage() {
    // given
    int numberOfInstances = 10;
    String reportId = generateValidReport(numberOfInstances);

    // when
    Response response = publicApiClient.exportReport(reportId, getAccessToken(), numberOfInstances, null);
    PaginatedDataExportDto data = response.readEntity(PaginatedDataExportDto.class);

    // then
    assertThat(data.getNumberOfRecordsInResponse()).isEqualTo(numberOfInstances);
    assertThat((long) data.getNumberOfRecordsInResponse()).isEqualTo(data.getTotalNumberOfRecords());
    assertThat(data.getSearchRequestId()).isNotBlank();
    assertThat(response.getStatus())
      .isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(data.getMessage()).isNull();
    assertThat(data.getReportId()).isEqualTo(reportId);
  }

  @Test
  public void rawDataReportExportResultInSeveralPages() {
    // given
    int numberOfInstances = 11;
    int limit = numberOfInstances / 2;
    String reportId = generateValidReport(numberOfInstances);

    // when
    Response responsePage1 = publicApiClient.exportReport(reportId, getAccessToken(), limit, null);
    PaginatedDataExportDto dataPage1 = responsePage1.readEntity(PaginatedDataExportDto.class);

    Response responsePage2 = publicApiClient.exportReport(reportId, getAccessToken(), null,
                                                          dataPage1.getSearchRequestId()
    );
    PaginatedDataExportDto dataPage2 = responsePage2.readEntity(PaginatedDataExportDto.class);

    Response responsePage3 = publicApiClient.exportReport(reportId, getAccessToken(), null,
                                                          dataPage2.getSearchRequestId()
    );
    PaginatedDataExportDto dataPage3 = responsePage3.readEntity(PaginatedDataExportDto.class);

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
    assertThat(dataPage1.getMessage()).isNull();
    assertThat(dataPage1.getReportId()).isEqualTo(reportId);
    assertThat(dataPage2.getMessage()).isNull();
    assertThat(dataPage2.getReportId()).isEqualTo(reportId);
    assertThat(dataPage3.getMessage()).isNull();
    assertThat(dataPage3.getReportId()).isEqualTo(reportId);
  }

  @Test
  public void rawDataExportPaginatingWithInvalidScrollId() {
    // given
    int numberOfInstances = 2;
    String reportId = generateValidReport(numberOfInstances);

    // when
    // Providing a non-existing scrollId
    Response response = publicApiClient.exportReport(reportId, getAccessToken(), numberOfInstances,
                                                     "NoSoupForYou!"
    );

    // then
    assertThat(response.getStatus())
      .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  @SneakyThrows
  public void rawDataExportPaginatingWithExpiredScrollId() {

    // given
    int numberOfInstances = 3;
    int limit = numberOfInstances / 2;
    String reportId = generateValidReport(numberOfInstances);

    // when
    Response responsePage1 = publicApiClient.exportReport(reportId, getAccessToken(), limit, null);
    PaginatedDataExportDto dataPage1 = responsePage1.readEntity(PaginatedDataExportDto.class);

    ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
    clearScrollRequest.addScrollId(dataPage1.getSearchRequestId());
    ClearScrollResponse clearScrollResponse = embeddedOptimizeExtension.getOptimizeElasticClient()
      .clearScroll(clearScrollRequest);
    boolean succeeded = clearScrollResponse.isSucceeded();

    Response responsePage2 = publicApiClient.exportReport(reportId, getAccessToken(), null,
                                                          dataPage1.getSearchRequestId()
    );

    // then
    assert (succeeded);
    assertThat(responsePage1.getStatus())
      .isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(dataPage1.getSearchRequestId()).isNotBlank();
    assertThat(dataPage1.getMessage()).isNull();
    assertThat(dataPage1.getReportId()).isEqualTo(reportId);
    assertThat(responsePage2.getStatus())
      .isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void rawDataExportPaginatingWhenResultsExhausted() {
    // given
    int numberOfInstances = 10;
    String reportId = generateValidReport(numberOfInstances);

    // when
    // This retrieves all results, since limit is 3 times as big as number of instances
    Response response = publicApiClient.exportReport(reportId, getAccessToken(), numberOfInstances * 3, null);
    PaginatedDataExportDto data = response.readEntity(PaginatedDataExportDto.class);

    //Now there are no results left, but I try to get them anyway
    Response responsePage2 = publicApiClient.exportReport(reportId, getAccessToken(), null, data.getSearchRequestId());
    PaginatedDataExportDto dataPage2 = responsePage2.readEntity(PaginatedDataExportDto.class);

    // then
    assertThat(data.getNumberOfRecordsInResponse()).isEqualTo(numberOfInstances);
    assertThat((long) data.getNumberOfRecordsInResponse()).isEqualTo(data.getTotalNumberOfRecords());
    assertThat(data.getSearchRequestId()).isNotBlank();
    assertThat(response.getStatus())
      .isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(dataPage2.getTotalNumberOfRecords()).isEqualTo(numberOfInstances);
    assertThat(dataPage2.getNumberOfRecordsInResponse()).isZero();
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
      .buildPublicExportJsonReportResultRequest(reportId, getAccessToken())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void hyperMapFrequencyReportHasExpectedValue() {
    // given
    initHypermapTests();
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithDefaultAndTaskB2WithSecondUser(processInstanceDto);
    importAllEngineEntitiesFromScratch();
    final ProcessReportDataDto reportData = createFrequencyReport(processDefinition);
    String reportId = createNewSingleMapReport(reportData);

    // when
    Response response = publicApiClient.exportReport(reportId, getAccessToken(), null, null);
    PaginatedDataExportDto data = response.readEntity(PaginatedDataExportDto.class);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(data.getNumberOfRecordsInResponse()).isEqualTo(2);
    assertThat((long) data.getNumberOfRecordsInResponse()).isEqualTo(data.getTotalNumberOfRecords());
    assertThat(data.getSearchRequestId()).isNull();
    assertThat(data.getMessage()).isNull();
    assertThat(data.getReportId()).isEqualTo(reportId);
    assertThat(data.getData()).isInstanceOf(List.class);
    List<?> nestedData = (List<?>) data.getData();
    assertThat(nestedData.size()).isEqualTo(2);
  }


  @Test
  public void hyperMapDurationReportHasExpectedValue() {
    // given
    initHypermapTests();
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithDefaultAndTaskB2WithSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, 10L);
    importAllEngineEntitiesFromScratch();
    final ProcessReportDataDto reportData = createDurationReport(processDefinition);
    String reportId = createNewSingleMapReport(reportData);

    // when
    Response response = publicApiClient.exportReport(reportId, getAccessToken(), 5, null);
    PaginatedDataExportDto data = response.readEntity(PaginatedDataExportDto.class);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(data.getNumberOfRecordsInResponse()).isEqualTo(2);
    assertThat((long) data.getNumberOfRecordsInResponse()).isEqualTo(data.getTotalNumberOfRecords());
    assertThat(data.getSearchRequestId()).isNull();
    assertThat(data.getMessage()).isNull();
    assertThat(data.getReportId()).isEqualTo(reportId);
    assertThat(data.getData()).isInstanceOf(List.class);
    List<?> nestedData = (List<?>) data.getData();
    assertThat(nestedData.size()).isEqualTo(2);
  }

  @Test
  public void numberReportExportWorksEvenWithNoData() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    final CanceledInstancesOnlyFilterDto filter = new CanceledInstancesOnlyFilterDto();
    filter.setFilterLevel(FilterApplicationLevel.INSTANCE);
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      // We use a canceled instances filter to remove instance data
      .setFilter(filter)
      .build();

    importAllEngineEntitiesFromScratch();
    String reportId = createAndStoreDefaultReportDefinition(reportData);

    // when
    Response response = publicApiClient.exportReport(reportId, getAccessToken(), 5, null);
    PaginatedDataExportDto data = response.readEntity(PaginatedDataExportDto.class);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat((long) data.getNumberOfRecordsInResponse()).isEqualTo(data.getTotalNumberOfRecords()).isZero();
    assertThat(data.getSearchRequestId()).isNull();
    assertThat(data.getMessage()).isNull();
    assertThat(data.getReportId()).isEqualTo(reportId);
  }

  @Test
  public void numberReportExportWorks() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .build();

    importAllEngineEntitiesFromScratch();
    String reportId = createAndStoreDefaultReportDefinition(reportData);

    // when
    Response response = publicApiClient.exportReport(reportId, getAccessToken(), 5, null);
    PaginatedDataExportDto responseObject = response.readEntity(PaginatedDataExportDto.class);

    // then
    assertThat((long) responseObject.getNumberOfRecordsInResponse()).
      isEqualTo(responseObject.getTotalNumberOfRecords()).isEqualTo(1);
    assertThat(responseObject.getSearchRequestId()).isNull();
    assertThat(responseObject.getMessage()).isNull();
    assertThat(responseObject.getReportId()).isEqualTo(reportId);
    assertThat(responseObject.getData()).isEqualTo(1.0);
  }

  @Test
  public void combinedReportFailsGracefullyWhenExporting() {
    // given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    ProcessInstanceEngineDto processInstance2 = engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(
      "aProcess",
      START,
      END
    ));
    String singleReportId1 = createNewSingleMapReport(processInstance1);
    String singleReportId2 = createNewSingleMapReport(processInstance2);
    String combinedReportId = reportClient.createNewCombinedReport(singleReportId1, singleReportId2);
    importAllEngineEntitiesFromScratch();

    // when
    ErrorResponseDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleQueryParam(QUERY_LIMIT_PARAM, 5)
      .buildPublicExportJsonReportResultRequest(combinedReportId, getAccessToken())
      .execute(ErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());
    String errorMessage = response.getDetailedMessage();

    // then
    assertThat(errorMessage).isEqualToIgnoringCase("Combined reports cannot be exported as Json");
  }

  @Test
  public void userIsInformedWhenLimitIsIgnoredForNonRawDataExports() {
    // given
    initHypermapTests();
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithDefaultAndTaskB2WithSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, 10L);

    importAllEngineEntitiesFromScratch();
    final ProcessReportDataDto reportData = createDurationReport(processDefinition);
    String reportId = createNewSingleMapReport(reportData);

    // when
    Response response = publicApiClient.exportReport(reportId, getAccessToken(), 1, null);
    PaginatedDataExportDto data = response.readEntity(PaginatedDataExportDto.class);

    // then
    assertThat(data.getMessage()).
      isEqualToIgnoringCase("All records are delivered in this response regardless of the set limit, since result " +
                              "pagination is only supported for raw data reports.");
    assertThat(data.getNumberOfRecordsInResponse()).isEqualTo(2);
    assertThat((long) data.getNumberOfRecordsInResponse()).isEqualTo(data.getTotalNumberOfRecords());
    assertThat(data.getSearchRequestId()).isNull();
    assertThat(data.getReportId()).isEqualTo(reportId);
    assertThat(data.getData()).isInstanceOf(List.class);
    List<?> nestedData = (List<?>) data.getData();
    assertThat(nestedData.size()).isEqualTo(2);
  }

  @Test
  public void exportNonExistingReportResult() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildPublicExportJsonReportResultRequest("IWishIExisted_ButIDont", getAccessToken())
      .execute();
    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  private String extractFirstProcessInstanceId(List<?> data) {
    Object firstData = data.get(0);
    if (firstData instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> firstDataPair = (Map<String, String>) firstData;
      return firstDataPair.get("processInstanceId");
    }
    return "";
  }

  private String createAndStoreDefaultReportDefinition(ProcessReportDataDto reportData) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportData);
    singleProcessReportDefinitionDto.setId("something");
    singleProcessReportDefinitionDto.setLastModifier("something");
    singleProcessReportDefinitionDto.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleProcessReportDefinitionDto.setCreated(someDate);
    singleProcessReportDefinitionDto.setLastModified(someDate);
    singleProcessReportDefinitionDto.setOwner("something");
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
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
          embeddedOptimizeExtension.getConfigurationService().getOptimizeApiConfiguration().getAccessToken())
        .orElseGet(() -> {
          String randomToken = "1_2_Polizei";
          embeddedOptimizeExtension.getConfigurationService().getOptimizeApiConfiguration().setAccessToken(randomToken);
          return randomToken;
        });
  }

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

  private ProcessDefinitionEngineDto deployFourUserTasksDefinition() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .parallelGateway()
      .userTask(USER_TASK_1)
      .userTask(USER_TASK_2)
      .endEvent()
      .moveToLastGateway()
      .userTask(USER_TASK_A)
      .userTask(USER_TASK_B)
      .endEvent()
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  @SuppressWarnings(SAME_PARAM_VALUE)
  private void changeDuration(ProcessInstanceEngineDto processInstanceDto, long millis) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(
        historicUserTaskInstanceDto ->
        {
          try {
            engineDatabaseExtension.changeUserTaskAssigneeClaimOperationTimestamp(
              historicUserTaskInstanceDto.getId(),
              historicUserTaskInstanceDto.getStartTime().plus(millis, ChronoUnit.MILLIS)
            );
          } catch (SQLException ex) {
            throw new RuntimeException(ex);
          }
        });
  }

  private String createNewSingleMapReport(ProcessReportDataDto data) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setName("FooName");
    singleProcessReportDefinitionDto.setData(data);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    return createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
  }

  private ProcessReportDataDto createFrequencyReport(final ProcessDefinitionEngineDto processDefinition) {
    return createFrequencyReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private ProcessReportDataDto createDurationReport(final String processDefinitionKey, final String version) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(version)
      .setUserTaskDurationTime(UserTaskDurationTime.IDLE)
      .setReportDataType(ProcessReportDataType.USER_TASK_DUR_GROUP_BY_ASSIGNEE_BY_USER_TASK)
      .build();
  }

  private ProcessReportDataDto createDurationReport(final ProcessDefinitionEngineDto processDefinition) {
    return createDurationReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private void finishUserTask1AWithDefaultAndTaskB2WithSecondUser(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto1.getId()
    );
    // finish user task 2 and B with second user
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USERS_PASSWORD,
      processInstanceDto1.getId()
    );
  }

  private ProcessReportDataDto createFrequencyReport(final String processDefinitionKey, final String version) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(version)
      .setReportDataType(ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_ASSIGNEE_BY_USER_TASK)
      .build();
  }

  public void initHypermapTests() {
    // create second user
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWith5FlowNodes() {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START)
      .serviceTask("ServiceTask1")
      .camundaExpression("${true}")
      .serviceTask("ServiceTask2")
      .camundaExpression("${true}")
      .serviceTask("ServiceTask3")
      .camundaExpression("${true}")
      .endEvent(END)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcess(processModel);
  }
}