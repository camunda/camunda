package org.camunda.optimize.service.security;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedMapReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.util.ReportDataHelper.createCombinedReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.camunda.optimize.test.util.ReportDataHelper.createReportDataViewRawAsTable;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


public class ReportAuthorizationIT {

   public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void evaluateUnauthorizedStoredReport() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployAndStartSimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String reportId = createReportForDefinition("aprocess");

    // when
    Response response = embeddedOptimizeRule.target("report/" + reportId + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, createAuthenticationHeaderForKermit())
      .get();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void deleteUnauthorizedStoredReport() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployAndStartSimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String reportId = createReportForDefinition("aprocess");

    // when
    Response response = embeddedOptimizeRule.target("report/" + reportId)
      .request()
      .header(HttpHeaders.AUTHORIZATION, createAuthenticationHeaderForKermit())
      .delete();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void evaluateUnauthorizedOnTheFlyReport() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployAndStartSimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDefinitionDto definition = constructReportWithDefinition("aprocess");
    Response response = embeddedOptimizeRule.target("report/evaluate/single")
      .request()
      .header(HttpHeaders.AUTHORIZATION, createAuthenticationHeaderForKermit())
      .post(Entity.json(definition.getData()));

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void updateUnauthorizedReport() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployAndStartSimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String reportId = createReportForDefinition("aprocess");
    ReportDefinitionDto updatedReport = createReportUpdate();

    // when
    Response response = embeddedOptimizeRule.target("report/" + reportId)
      .request()
      .header(HttpHeaders.AUTHORIZATION, createAuthenticationHeaderForKermit())
      .put(Entity.json(updatedReport));

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void getUnauthorizedReport() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployAndStartSimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String reportId = createReportForDefinition("aprocess");

    // when
    Response response = embeddedOptimizeRule.target("report/" + reportId)
      .request()
      .header(HttpHeaders.AUTHORIZATION, createAuthenticationHeaderForKermit())
      .get();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void shareUnauthorizedReport() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployAndStartSimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String reportId = createReportForDefinition("aprocess");
    ReportShareDto reportShareDto = new ReportShareDto();
    reportShareDto.setReportId(reportId);

    // when
    Response response = embeddedOptimizeRule.target("share/report")
      .request()
      .header(HttpHeaders.AUTHORIZATION, createAuthenticationHeaderForKermit())
      .post(Entity.json(reportShareDto));

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void newReportCanBeAccessedByEveryone() throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployAndStartSimpleProcessDefinition("aprocess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String reportId = createNewReport();

    // when
    Response response = embeddedOptimizeRule.target("report/" + reportId)
      .request()
      .header(HttpHeaders.AUTHORIZATION, createAuthenticationHeaderForKermit())
      .get();

    // then
    assertThat(response.getStatus(), is(200));

    // when
    response = embeddedOptimizeRule.target("report/")
      .request()
      .header(HttpHeaders.AUTHORIZATION, createAuthenticationHeaderForKermit())
      .get();

    // then
    assertThat(response.getStatus(), is(200));
    List<ReportDefinitionDto> reports = response.readEntity(new GenericType<List<ReportDefinitionDto>>(){});
    assertThat(reports.size(), is(1));
  }

  @Test
  public void unauthorizedReportInCombinedIsNotEvaluated() {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployAndStartSimpleProcessDefinition("aprocess");
    grantSingleDefinitionAuthorizationsForUser("kermit", "aprocess");
    deployAndStartSimpleProcessDefinition("notAuthorizedProcess");
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String authorizedReportId = createNewSingleMapReport("aprocess");
    String notAuthorizedReportId = createNewSingleMapReport("notAuthorizedProcess");

    // when
    CombinedReportDataDto combinedReport = createCombinedReport(authorizedReportId, notAuthorizedReportId);
    Response response = embeddedOptimizeRule.target("report/evaluate/combined")
      .request()
      .header(HttpHeaders.AUTHORIZATION, createAuthenticationHeaderForKermit())
      .post(Entity.json(combinedReport));

    // then
    assertThat(response.getStatus(), is(200));
    CombinedMapReportResultDto result = response.readEntity(CombinedMapReportResultDto.class);
    Map<String, Map<String, Long>> resultMap = result.getResult();
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.containsKey(notAuthorizedReportId), is(false));
    Map<String, Long> flowNodeToCount = resultMap.get(authorizedReportId);
    assertThat(flowNodeToCount.size(), is(2));
  }

  private void grantSingleDefinitionAuthorizationsForUser(String userId, String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private String createNewCombinedReport() {
    Response response =
      embeddedOptimizeRule.target("report/combined")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  private String createNewSingleMapReport(String processDefinitionKey) {
    String singleReportId = createNewReport();
    SingleReportDataDto countFlowNodeFrequencyGroupByFlowNode =
      createCountFlowNodeFrequencyGroupByFlowNode(processDefinitionKey, "1");
    SingleReportDefinitionDto definitionDto = new SingleReportDefinitionDto();
    definitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private void deployAndStartSimpleProcessDefinition(String processId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
      .startEvent()
      .endEvent()
      .done();
    engineRule.deployAndStartProcess(modelInstance);
  }

  public ReportDefinitionDto createReportUpdate() {
    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setProcessDefinitionKey("procdef");
    reportData.setProcessDefinitionVersion("123");
    reportData.setFilter(Collections.emptyList());
    reportData.setConfiguration("aRandomConfiguration");
    SingleReportDefinitionDto report = new SingleReportDefinitionDto();
    report.setData(reportData);
    report.setName("MyReport");
    return report;
  }

  private String createReportForDefinition(String definitionKey) {
    String id = createNewReport();
    ReportDefinitionDto definition = constructReportWithDefinition(definitionKey);
    updateReport(id, definition);
    return id;
  }

  public String createNewReport() {
    Response response =
      embeddedOptimizeRule.target("report/single")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));
    return response.readEntity(IdDto.class).getId();
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = getUpdateReportResponse(id, updatedReport);
    assertThat(response.getStatus(), is(204));
  }

  private ReportDefinitionDto constructReportWithDefinition(String processDefinitionKey) {
    SingleReportDefinitionDto reportDefinitionDto = new SingleReportDefinitionDto();
    SingleReportDataDto data = createReportDataViewRawAsTable(processDefinitionKey, "1");
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  private Response getUpdateReportResponse(String id, ReportDefinitionDto updatedReport) {
    return embeddedOptimizeRule.target("report/" + id)
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .put(Entity.json(updatedReport));
  }

  private String createAuthenticationHeaderForKermit() {
    String token = embeddedOptimizeRule.authenticateUser("kermit", "kermit");
    return "Bearer " + token;
  }

  private void addKermitUserAndGrantAccessToOptimize() {
    engineRule.addUser("kermit", "kermit");
    engineRule.grantUserOptimizeAccess("kermit");
  }

}
