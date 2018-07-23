package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
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
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


public class StartDateQueryFilterIT {

  private static final String TEST_ACTIVITY = "testActivity";
  private static final long TIME_OFFSET_MILLS = 2000L;

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);


  private OffsetDateTime past;
  private String processDefinitionKey;
  private String processDefinitionVersion;

  @Test
  public void testGetHeatMapWithGteStartDateCriteria() throws Exception {
    //given
    startAndImportSimpleProcess();

    //when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    List<FilterDto> fixedStartDateFilter =
        DateUtilHelper.createFixedStartDateFilter(past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS), OffsetDateTime.now());
    reportData.setFilter(fixedStartDateFilter);
    RawDataReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 0);

    //when
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(past, null));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS), null));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 1);
  }

  @Test
  public void testGetHeatMapWithLteStartDateCriteria() throws Exception {
    //given
    startAndImportSimpleProcess();

    //when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(null, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    RawDataReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(null, past));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(null, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 0);
  }

  @Test
  public void testGetHeatMapWithMixedDateCriteria() throws Exception {
    //given
    startAndImportSimpleProcess();

    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS), null));

    //when
    RawDataReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 1);

    //given
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(null, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));

    //when
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