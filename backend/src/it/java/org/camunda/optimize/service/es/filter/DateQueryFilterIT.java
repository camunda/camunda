package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataSingleReportResultDto;
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


public class DateQueryFilterIT {

  private static final String TEST_ACTIVITY = "testActivity";
  private static final long TIME_OFFSET_MILLS = 2000L;

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);


  private OffsetDateTime start;
  private OffsetDateTime end;
  private String processDefinitionKey;
  private String processDefinitionVersion;

  @Test
  public void testGetHeatMapWithGteStartDateCriteria() {
    //given
    startAndImportSimpleProcess();

    //when
    SingleReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    List<FilterDto> fixedStartDateFilter =
        DateUtilHelper.createFixedStartDateFilter(start.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS), OffsetDateTime.now());
    reportData.setFilter(fixedStartDateFilter);
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 0);

    //when
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(start, null));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(start.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS), null));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 1);
  }

  @Test
  public void testGetHeatMapWithLteStartDateCriteria() {
    //given
    startAndImportSimpleProcess();

    //when
    SingleReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(null, start.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(null, start));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(null, start.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 0);
  }

  @Test
  public void testGetHeatMapWithGteEndDateCriteria() throws Exception {
    //given
    startAndImportSimpleProcess();

    //when
    SingleReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(DateUtilHelper.createFixedEndDateFilter(null, end.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createFixedEndDateFilter(end, null));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createFixedEndDateFilter(end.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS), null));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 0);
  }

  @Test
  public void testGetHeatMapWithLteEndDateCriteria() throws Exception {
    //given
    startAndImportSimpleProcess();

    //when
    SingleReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(DateUtilHelper.createFixedEndDateFilter(end.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS), null));
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(DateUtilHelper.createFixedEndDateFilter(end.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS), null));
    result = evaluateReport(reportData);
    //then
    assertResults(result, 0);
  }

  @Test
  public void testGetHeatMapWithMixedDateCriteria() {
    //given
    startAndImportSimpleProcess();

    SingleReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(start.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS), null));

    //when
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    //then
    assertResults(result, 1);

    //given
    reportData.setFilter(DateUtilHelper.createFixedEndDateFilter(end.minusSeconds(200L), null));

    //when
    result = evaluateReport(reportData);

    //then
    assertResults(result, 1);

    //given
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(null, start.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS)));

    //when
    result = evaluateReport(reportData);

    //then
    assertResults(result, 0);
  }


  private void startAndImportSimpleProcess() {
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    HistoricProcessInstanceDto processInstance = engineRule.getHistoricProcessInstance(processInstanceDto.getId());
    start = processInstance.getStartTime();
    end = processInstance.getEndTime();
    processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
  }


  private void assertResults(RawDataSingleReportResultDto resultMap, int size) {
    assertThat(resultMap.getResult().size(), is(size));
  }

  private RawDataSingleReportResultDto evaluateReport(SingleReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    MatcherAssert.assertThat(response.getStatus(), is(200));
    return response.readEntity(RawDataSingleReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(SingleReportDataDto reportData) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSingleUnsavedReportRequest(reportData)
            .execute();
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