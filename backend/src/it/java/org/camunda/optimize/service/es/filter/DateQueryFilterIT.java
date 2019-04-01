package org.camunda.optimize.service.es.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

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
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);

    List<ProcessFilterDto> fixedStartDateFilter =
      ProcessFilterBuilder.filter()
        .fixedStartDate()
        .start(start.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
        .end(OffsetDateTime.now())
        .add()
        .buildList();
    reportData.setFilter(fixedStartDateFilter);
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    //then
    assertResults(result, 0);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(start).end(null).add().buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(start.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .end(null)
                           .add()
                           .buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 1);
  }

  @Test
  public void testGetHeatMapWithLteStartDateCriteria() {
    //given
    startAndImportSimpleProcess();

    //when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable
      (processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .add()
                           .buildList());
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(null).end(start).add().buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .add()
                           .buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 0);
  }

  @Test
  public void testGetHeatMapWithGteEndDateCriteria() throws Exception {
    //given
    startAndImportSimpleProcess();

    //when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);

    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(null)
                           .end(end.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .add()
                           .buildList());
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end)
                           .end(null)
                           .add()
                           .buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .end(null)
                           .add()
                           .buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 0);
  }

  @Test
  public void testGetHeatMapWithLteEndDateCriteria() throws Exception {
    //given
    startAndImportSimpleProcess();

    //when
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .end(null)
                           .add()
                           .buildList());
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .end(null)
                           .add()
                           .buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 0);
  }

  @Test
  public void testGetHeatMapWithMixedDateCriteria() {
    //given
    startAndImportSimpleProcess();

    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable
      (processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(start.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .end(null)
                           .add()
                           .buildList());

    //when
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    //then
    assertResults(result, 1);

    //given
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.minusSeconds(200L))
                           .end(null)
                           .add()
                           .buildList());

    //when
    result = evaluateReportAndReturnResult(reportData);

    //then
    assertResults(result, 1);

    //given
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .add()
                           .buildList());

    //when
    result = evaluateReportAndReturnResult(reportData);

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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
  }


  private void assertResults(RawDataProcessReportResultDto resultMap, int size) {
    assertThat(resultMap.getData().size(), is(size));
  }

  private RawDataProcessReportResultDto evaluateReportAndReturnResult(final ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {})
      // @formatter:on
      .getResult();
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