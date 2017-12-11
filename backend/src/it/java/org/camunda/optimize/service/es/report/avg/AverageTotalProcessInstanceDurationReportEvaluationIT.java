package org.camunda.optimize.service.es.report.avg;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.GroupByDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.filter.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.util.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.result.NumberReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_NONE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_AVERAGE_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_DURATION_PROPERTY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_PROCESS_INSTANCE_ENTITY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class AverageTotalProcessInstanceDurationReportEvaluationIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  private static final String TEST_ACTIVITY = "testActivity";

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void reportEvaluationForOneProcess() throws Exception {

    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    String processDefinitionId = processInstanceDto.getDefinitionId();
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    NumberReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(VIEW_AVERAGE_OPERATION));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_PROCESS_INSTANCE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_DURATION_PROPERTY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(GROUP_BY_NONE_TYPE));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(1000L));
  }

  @Test
  public void reportEvaluationById() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String reportId = createAndStoreDefaultReportDefinition(processDefinitionId);

    // when
    NumberReportResultDto result = evaluateReportById(reportId);

    // then
    ReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(VIEW_AVERAGE_OPERATION));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_PROCESS_INSTANCE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_DURATION_PROPERTY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(GROUP_BY_NONE_TYPE));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(1000L));
  }

  @Test
  public void evaluateReportForMultipleEvents() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    Map<String, OffsetDateTime> startDatesToUpdate = new HashMap<>();
    startDatesToUpdate.put(processInstanceDto.getId(), startDate);
    startDatesToUpdate.put(processInstanceDto2.getId(), startDate);
    startDatesToUpdate.put(processInstanceDto3.getId(), startDate);
    engineDatabaseRule.updateProcessInstanceStartDates(startDatesToUpdate);
    Map<String, OffsetDateTime> endDatesToUpdate = new HashMap<>();
    endDatesToUpdate.put(processInstanceDto.getId(), startDate.plusSeconds(1));
    endDatesToUpdate.put(processInstanceDto2.getId(), startDate.plusSeconds(2));
    endDatesToUpdate.put(processInstanceDto3.getId(), startDate.plusSeconds(3));
    engineDatabaseRule.updateProcessInstanceEndDates(endDatesToUpdate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    String processDefinitionId = processInstanceDto.getDefinitionId();
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    NumberReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(2000L));
  }

  @Test
  public void noAvailableProcessInstancesReturnsZero() throws Exception {
    // when
    ReportDataDto reportData = createDefaultReportData("fooProcessDefinitionId");
    NumberReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(0L));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(3));
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createDefaultReportData(processInstanceDto.getDefinitionId());
    NumberReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(2000L));
  }

  @Test
  public void dateFilterInReport() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getStartTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    reportData.setFilter(createDateFilter("<", "start_date", past));
    NumberReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(0L));

    // when
    reportData = createDefaultReportData(processDefinitionId);
    reportData.setFilter(createDateFilter(">=", "start_date", past));
    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(1000L));
  }

  public List<FilterDto> createDateFilter(String operator, String type, OffsetDateTime dateValue) {
    DateFilterDataDto date = new DateFilterDataDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(dateValue);

    DateFilterDto dateFilterDto = new DateFilterDto();
    dateFilterDto.setData(date);
    return Collections.singletonList(dateFilterDto);
  }

  @Test
  public void variableFilterInReport() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    reportData.setFilter(createVariableFilter());
    NumberReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(1000L));
  }

  private List<FilterDto> createVariableFilter() {
    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName("var");
    data.setType("boolean");
    data.setOperator("=");
    data.setValues(Collections.singletonList("true"));

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    return Collections.singletonList(variableFilterDto);
  }

  @Test
  public void flowNodeFilterInReport() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    OffsetDateTime startDate = OffsetDateTime.now();
    String processDefinitionId = deploySimpleGatewayProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinitionId, variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    variables.put("goToTask1", false);
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    List<ExecutedFlowNodeFilterDto> flowNodeFilter = ExecutedFlowNodeFilterBuilder.construct()
          .id("task1")
          .build();
    reportData.getFilter().addAll(flowNodeFilter);
    NumberReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(1000L));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() throws Exception {
    // given
    ReportDataDto dataDto = createDefaultReportData("123");
    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() throws Exception {
    // given
    ReportDataDto dataDto = createDefaultReportData("123");
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String activityId) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask(activityId)
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployAndStartProcess(processModel);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(Map<String, Object> variables) {
    return deployAndStartSimpleServiceTaskProcessWithVariables(TEST_ACTIVITY, variables);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(String activityId,
                                                                                       Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask(activityId)
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private String deploySimpleGatewayProcessDefinition() throws Exception {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent("startEvent")
      .exclusiveGateway("splittingGateway")
        .name("Should we go to task 1?")
        .condition("yes", "${goToTask1}")
        .serviceTask("task1")
          .camundaExpression("${true}")
      .exclusiveGateway("mergeGateway")
        .endEvent("endEvent")
      .moveToNode("splittingGateway")
        .condition("no", "${!goToTask1}")
        .serviceTask("task2")
          .camundaExpression("${true}")
        .connectTo("mergeGateway")
      .done();
    return engineRule.deployProcessAndGetId(modelInstance);
  }

  private ReportDataDto createDefaultReportData(String processDefinitionId) {
    ReportDataDto reportData = new ReportDataDto();
    reportData.setProcessDefinitionId(processDefinitionId);
    reportData.setVisualization("heat");
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_AVERAGE_OPERATION);
    view.setEntity(VIEW_PROCESS_INSTANCE_ENTITY);
    view.setProperty(VIEW_DURATION_PROPERTY);
    reportData.setView(view);
    GroupByDto groupByDto = new GroupByDto();
    groupByDto.setType(GROUP_BY_NONE_TYPE);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  private NumberReportResultDto evaluateReport(ReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(NumberReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ReportDataDto reportData) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    return embeddedOptimizeRule.target("report/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .post(Entity.json(reportData));
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionId) {
    String id = createNewReport();
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
    report.setId(id);
    report.setLastModifier("something");
    report.setName("something");
    report.setCreated(OffsetDateTime.now());
    report.setLastModified(OffsetDateTime.now());
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }

  private String createNewReport() {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  private NumberReportResultDto evaluateReportById(String reportId) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response = embeddedOptimizeRule.target("report/" + reportId + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .get();
    assertThat(response.getStatus(), is(200));

    return response.readEntity(NumberReportResultDto.class);
  }

}
