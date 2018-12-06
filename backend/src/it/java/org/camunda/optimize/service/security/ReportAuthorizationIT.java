package org.camunda.optimize.service.security;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.MapProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createCombinedReport;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createProcessReportDataViewRawAsTable;
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
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication("kermit", "kermit")
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

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
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication("kermit", "kermit")
      .buildDeleteReportRequest(reportId)
      .execute();

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
    SingleReportDefinitionDto<ProcessReportDataDto> definition = constructReportWithDefinition("aprocess");
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication("kermit", "kermit")
      .buildEvaluateSingleUnsavedReportRequest(definition.getData())
      .execute();

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
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication("kermit", "kermit")
      .buildUpdateReportRequest(reportId, updatedReport)
      .execute();

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
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication("kermit", "kermit")
      .buildGetReportRequest(reportId)
      .execute();

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
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildShareReportRequest(reportShareDto)
      .withUserAuthentication("kermit", "kermit")
      .execute();

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
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    // when
    List<ReportDefinitionDto> reports = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetReportRequest(reportId)
      .withUserAuthentication("kermit", "kermit")
      .executeAndReturnList(ReportDefinitionDto.class, 200);

    // then
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

    CombinedProcessReportResultDto result = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(combinedReport)
      .withUserAuthentication("kermit", "kermit")
      .execute(CombinedProcessReportResultDto.class, 200);

    // then
    Map<String, MapProcessReportResultDto> resultMap = result.getResult();
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.containsKey(notAuthorizedReportId), is(false));
    Map<String, Long> flowNodeToCount = resultMap.get(authorizedReportId).getResult();
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


  private String createNewSingleMapReport(String processDefinitionKey) {
    String singleReportId = createNewReport();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode =
      createCountFlowNodeFrequencyGroupByFlowNode(processDefinitionKey, "1");
    SingleReportDefinitionDto<ProcessReportDataDto> definitionDto = new SingleReportDefinitionDto<>();
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
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey("procdef");
    reportData.setProcessDefinitionVersion("123");
    reportData.setFilter(Collections.emptyList());
    reportData.setConfiguration("aRandomConfiguration");
    SingleReportDefinitionDto<ProcessReportDataDto> report = new SingleReportDefinitionDto<>();
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
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = getUpdateReportResponse(id, updatedReport);
    assertThat(response.getStatus(), is(204));
  }

  private SingleReportDefinitionDto<ProcessReportDataDto> constructReportWithDefinition(String processDefinitionKey) {
    SingleReportDefinitionDto<ProcessReportDataDto> reportDefinitionDto = new SingleReportDefinitionDto<>();
    ProcessReportDataDto data = createProcessReportDataViewRawAsTable(processDefinitionKey, "1");
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  private Response getUpdateReportResponse(String id, ReportDefinitionDto updatedReport) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateReportRequest(id, updatedReport)
      .execute();
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
