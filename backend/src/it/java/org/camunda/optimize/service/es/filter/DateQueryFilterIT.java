package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticsearchIntegrationRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN_EQUALS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


public class DateQueryFilterIT {

  private static final String TEST_ACTIVITY = "testActivity";
  private static final long TIME_OFFSET_MILLS = 2000L;

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticsearchIntegrationRule elasticSearchRule = new ElasticsearchIntegrationRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);


  private OffsetDateTime past;
  private String processDefinitionKey;
  private String processDefinitionVersion;

  @Test
  public void testGetReportWithLtStartDateCriteria() throws Exception {
    //given
    startAndImportSimpleProcess();

    // when
    String operator = GREATER_THAN;
    String type = "start_date";

    //when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    RawDataReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 0);

    //when
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 0);

    //when
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 1);
  }

  @Test
  public void testGetHeatMapWithLteStartDateCriteria() throws Exception {
    //given
    startAndImportSimpleProcess();

    String operator = GREATER_THAN_EQUALS;
    String type = "start_date";

    //when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    RawDataReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 0);

    //when
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 1);
  }

  @Test
  public void testGetHeatMapWithLteEndDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getEndTime();
    processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String operator = GREATER_THAN_EQUALS;
    String type = "end_date";

    //when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    RawDataReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 0);

    //when
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 1);
  }

  @Test
  public void testGetHeatMapWithGtStartDateCriteria() throws Exception {
    //given
    startAndImportSimpleProcess();

    String operator = LESS_THAN;
    String type = "start_date";

    //when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    RawDataReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 0);

    //when
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 0);
  }

  @Test
  public void testGetHeatMapWithMixedDateCriteria() throws Exception {
    //given
    startAndImportSimpleProcess();

    String operator = GREATER_THAN;
    String type = "start_date";

    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));

    operator = LESS_THAN;
    type = "end_date";
    DateUtilHelper.addDateFilter(operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS), reportData);

    //when
    RawDataReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 1);

    //given
    operator = GREATER_THAN;
    type = "start_date";
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));

    operator = LESS_THAN;
    type = "end_date";
    DateUtilHelper.addDateFilter(operator, type, past, reportData);

    //when
    result = evaluateReport(reportData);

    //then
    assertResults(result, 0);
  }

  @Test
  public void testGetHeatMapWithGtEndDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getEndTime();
    processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String operator = LESS_THAN;
    String type = "end_date";

    //when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    RawDataReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 0);

    //when
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 0);
  }

  @Test
  public void testGetHeatMapWithGteStartDateCriteria() throws Exception {
    //given
    startAndImportSimpleProcess();

    String operator = LESS_THAN_EQUALS;
    String type = "start_date";

    //when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    RawDataReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createDateFilter(operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 0);
  }

  private void startAndImportSimpleProcess() throws InterruptedException {
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getStartTime();
    processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
  }


  private void assertResults(RawDataReportResultDto resultMap, int size) {
    assertThat(resultMap.getResult().size(), is(size));
  }

  private RawDataReportResultDto evaluateReport(ReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    MatcherAssert.assertThat(response.getStatus(), is(200));
    return response.readEntity(RawDataReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ReportDataDto reportData) {
    return embeddedOptimizeRule.target("report/evaluate")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(reportData));
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
}