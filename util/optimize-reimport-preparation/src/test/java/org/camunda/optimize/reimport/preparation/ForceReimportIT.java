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
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
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
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ForceReimportIT {

  private ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  private EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  private EngineIntegrationRule engineRule = new EngineIntegrationRule("reimport-preparation.properties");

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void forceReimport() throws
                              IOException,
                              URISyntaxException {

    //given
    ProcessDefinitionEngineDto processDefinitionEngineDto = deployAndStartSimpleServiceTask();
    String reportId = createAndStoreNumberReport(processDefinitionEngineDto);
    AlertCreationDto alert = setupBasicAlert(reportId);
    addAlertToOptimize(alert);
    createNewDashboard();
    addLicense();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<SingleReportDefinitionDto> reports = getAllReports();
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

  private boolean hasEngineData() {
    ConfigurationService configurationService = embeddedOptimizeRule.getConfigurationService();

    List<String> types = new ArrayList<>();
    types.add(TIMESTAMP_BASED_IMPORT_INDEX_TYPE);
    types.add(configurationService.getImportIndexType());
    types.add(configurationService.getProcessDefinitionType());
    types.add(configurationService.getProcessInstanceType());

    List<String> indexNames = types
      .stream()
      .map(configurationService::getOptimizeIndex)
      .collect(Collectors.toList());

    SearchResponse response = elasticSearchRule.getClient()
      .prepareSearch(indexNames.toArray(new String[0]))
      .setTypes(types.toArray(new String[0]))
      .setQuery(QueryBuilders.matchAllQuery())
      .get();

    return response.getHits().getTotalHits() > 0L;
  }

  private boolean licenseExists() {
    ConfigurationService configurationService = embeddedOptimizeRule.getConfigurationService();
    GetResponse response = elasticSearchRule.getClient().prepareGet(
      configurationService.getOptimizeIndex(configurationService.getLicenseType()),
      configurationService.getLicenseType(),
      configurationService.getLicenseType()
    )
      .get();
    return response.isExists();
  }

  private List<AlertDefinitionDto> getAllAlerts() {
    Response response =
      embeddedOptimizeRule.target("alert")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<AlertDefinitionDto>>() {
    });
  }

  private String readFileToString() throws IOException, URISyntaxException {
    return new String(
      Files.readAllBytes(Paths.get(getClass().getResource("/license/ValidTestLicense.txt").toURI())),
      StandardCharsets.UTF_8
    );
  }

  private void addLicense() throws IOException, URISyntaxException {
    String license = readFileToString();
    Entity<String> entity = Entity.entity(license, MediaType.TEXT_PLAIN);

    // when
    Response response =
      embeddedOptimizeRule.target("license/validate-and-store")
        .request()
        .post(entity);
  }

  private AlertCreationDto setupBasicAlert(String reportId) {
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    return createSimpleAlert(reportId);
  }

  private String createNewReportHelper() {
    Response response =
      embeddedOptimizeRule.target("report/single")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  private String createAndStoreNumberReport(ProcessDefinitionEngineDto processDefinition) {
    String id = createNewReportHelper();
    ReportDefinitionDto report = getReportDefinitionDto(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion())
    );
    updateReport(id, report);
    return id;
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }

  protected SingleReportDefinitionDto getReportDefinitionDto(ProcessDefinitionEngineDto processDefinition) {
    return getReportDefinitionDto(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }


  private SingleReportDefinitionDto getReportDefinitionDto(String processDefinitionKey,
                                                           String processDefinitionVersion) {
    SingleReportDataDto reportData =
      ReportDataHelper.createPiFrequencyCountGroupedByNoneAsNumber(processDefinitionKey, processDefinitionVersion);
    SingleReportDefinitionDto report = new SingleReportDefinitionDto();
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

  private void addAlertToOptimize(AlertCreationDto creationDto) {
    Response response =
        embeddedOptimizeRule.target("alert")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .post(Entity.json(creationDto));

    String id = response.readEntity(IdDto.class).getId();
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
    WebTarget webTarget = embeddedOptimizeRule.target("dashboard");

    for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
      webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
    }
    Response response =
      webTarget
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<DashboardDefinitionDto>>() {
    });
  }

  private void createNewDashboard() {
    Response response =
      embeddedOptimizeRule.target("dashboard")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    response.readEntity(IdDto.class);
  }

  private void forceReimportOfEngineData() throws IOException {
    ReimportPreparation.main(new String[]{});
  }

  private List<SingleReportDefinitionDto> getAllReports() {
    return getAllReportsWithQueryParam(new HashMap<>());
  }

  private List<SingleReportDefinitionDto> getAllReportsWithQueryParam(Map<String, Object> queryParams) {
    WebTarget webTarget = embeddedOptimizeRule.target("report");
    for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
      webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
    }

    Response response =
      webTarget
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<SingleReportDefinitionDto>>() {
    });
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
