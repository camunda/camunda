/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.reimport.preparation;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IMPORT_INDEX_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LICENSE_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ForceReimportIT {

  private ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  private EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  private EngineIntegrationRule engineRule = new EngineIntegrationRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void forceReimport() throws IOException, URISyntaxException {

    //given
    ProcessDefinitionEngineDto processDefinitionEngineDto = deployAndStartSimpleServiceTask();
    String reportId = createAndStoreNumberReport(processDefinitionEngineDto);
    AlertCreationDto alert = setupBasicAlert(reportId);
    embeddedOptimizeRule
      .getRequestExecutor().buildCreateAlertRequest(alert).execute();
    embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdDto.class, 200);
    addLicense();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ReportDefinitionDto> reports = getAllReports();
    List<DashboardDefinitionDto> dashboards = getAllDashboards();
    List<AlertDefinitionDto> alerts = getAllAlerts();

    // then
    assertThat(licenseExists(), is(true));
    assertThat(reports.size(), is(1));
    assertThat(dashboards.size(), is(1));
    assertThat(alerts.size(), is(1));
    assertThat(hasEngineData(), is(true));

    // when
    forceReimportOfEngineData();

    reports = getAllReports();
    dashboards = getAllDashboards();
    alerts = getAllAlerts();

    // then
    assertThat(licenseExists(), is(true));
    assertThat(reports.size(), is(1));
    assertThat(dashboards.size(), is(1));
    assertThat(alerts.size(), is(1));
    assertThat(hasEngineData(), is(false));
  }

  private boolean hasEngineData() throws IOException {
    List<String> types = new ArrayList<>();
    types.add(TIMESTAMP_BASED_IMPORT_INDEX_TYPE);
    types.add(IMPORT_INDEX_TYPE);
    types.add(PROCESS_DEFINITION_TYPE);
    types.add(PROC_INSTANCE_TYPE);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .size(0);
    SearchRequest searchRequest = new SearchRequest()
      .indices(types.toArray(new String[0]))
      .types(types.toArray(new String[0]))
      .source(searchSourceBuilder);

    SearchResponse response = elasticSearchRule.getOptimizeElasticClient()
      .search(searchRequest, RequestOptions.DEFAULT);

    return response.getHits().getTotalHits() > 0L;
  }

  private boolean licenseExists() {
    GetRequest getRequest = new GetRequest(LICENSE_TYPE, LICENSE_TYPE, LICENSE_TYPE);
    GetResponse getResponse;
    try {
      getResponse = elasticSearchRule.getOptimizeElasticClient().get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not retrieve license!", e);
    }
    return getResponse.isExists();
  }

  private List<AlertDefinitionDto> getAllAlerts() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllAlertsRequest()
      .executeAndReturnList(AlertDefinitionDto.class, 200);
  }

  private String readFileToString() throws IOException, URISyntaxException {
    return new String(
      Files.readAllBytes(Paths.get(getClass().getResource("/license/ValidTestLicense.txt").toURI())),
      StandardCharsets.UTF_8
    );
  }

  private void addLicense() throws IOException, URISyntaxException {
    String license = readFileToString();

    embeddedOptimizeRule.getRequestExecutor()
      .buildValidateAndStoreLicenseRequest(license)
      .execute();
  }

  private AlertCreationDto setupBasicAlert(String reportId) {
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    return createSimpleAlert(reportId);
  }

  private String createNewReportHelper() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createAndStoreNumberReport(ProcessDefinitionEngineDto processDefinition) {
    String id = createNewReportHelper();
    SingleProcessReportDefinitionDto report = getReportDefinitionDto(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion())
    );
    updateReport(id, report);
    return id;
  }

  private void updateReport(String id, SingleProcessReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  protected SingleProcessReportDefinitionDto getReportDefinitionDto(ProcessDefinitionEngineDto processDefinition) {
    return getReportDefinitionDto(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private SingleProcessReportDefinitionDto getReportDefinitionDto(String processDefinitionKey,
                                                                  String processDefinitionVersion) {
    ProcessReportDataDto reportData =
      ProcessReportDataBuilder
        .createReportData()
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessDefinitionVersion(processDefinitionVersion)
        .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
        .build();
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner("something");
    return report;
  }

  private AlertCreationDto createSimpleAlert(String reportId) {
    AlertCreationDto alertCreationDto = new AlertCreationDto();

    AlertInterval interval = new AlertInterval();
    interval.setUnit("Seconds");
    interval.setValue(1);
    alertCreationDto.setCheckInterval(interval);
    alertCreationDto.setThreshold(0);
    alertCreationDto.setThresholdOperator(">");
    alertCreationDto.setEmail("test@camunda.com");
    alertCreationDto.setName("test alert");
    alertCreationDto.setReportId(reportId);

    return alertCreationDto;
  }

  private List<DashboardDefinitionDto> getAllDashboards() {
    return getAllDashboardsWithQueryParam(new HashMap<>());
  }

  private List<DashboardDefinitionDto> getAllDashboardsWithQueryParam(Map<String, Object> queryParams) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .addQueryParams(queryParams)
      .buildGetAllDashboardsRequest()
      .executeAndReturnList(DashboardDefinitionDto.class, 200);
  }


  private void forceReimportOfEngineData() {
    ReimportPreparation.main(new String[]{});
  }

  private List<ReportDefinitionDto> getAllReports() {
    return getAllReportsWithQueryParam(new HashMap<>());
  }

  private List<ReportDefinitionDto> getAllReportsWithQueryParam(Map<String, Object> queryParams) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllReportsRequest()
      .addQueryParams(queryParams)
      .executeAndReturnList(ReportDefinitionDto.class, 200);
  }

  private ProcessDefinitionEngineDto deployAndStartSimpleServiceTask() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariables");
    return deployAndStartSimpleServiceTaskWithVariables(variables);
  }

  private ProcessDefinitionEngineDto deployAndStartSimpleServiceTaskWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();

    ProcessDefinitionEngineDto processDefinitionEngineDto =
      engineRule.deployProcessAndGetProcessDefinition(processModel);
    engineRule.startProcessInstance(processDefinitionEngineDto.getId(), variables);
    return processDefinitionEngineDto;
  }

}
