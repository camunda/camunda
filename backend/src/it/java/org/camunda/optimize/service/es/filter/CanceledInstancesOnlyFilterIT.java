package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataBuilderHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CanceledInstancesOnlyFilterIT {
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
          .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void mixedCanceledInstancesOnlyFilter() throws Exception {
    //given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst = engineRule.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineRule.startProcessInstance(userTaskProcess.getId());
    engineRule.startProcessInstance(userTaskProcess.getId());

    engineRule.externallyTerminateProcessInstance(firstProcInst.getId());
    engineDatabaseRule.changeProcessInstanceState(
            secondProcInst.getId(),
            CanceledInstancesOnlyQueryFilter.INTERNALLY_TERMINATED
    );


    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData =
            ReportDataBuilderHelper.createProcessReportDataViewRawAsTable(userTaskProcess.getKey(), String.valueOf(userTaskProcess.getVersion()));
    reportData.setFilter(Collections.singletonList(new CanceledInstancesOnlyFilterDto()));
    RawDataProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult().size(), is(2));
    List<String> resultProcDefIds = result.getResult()
            .stream()
            .map(RawDataProcessInstanceDto::getProcessInstanceId)
            .collect(Collectors.toList());

    assertThat(resultProcDefIds.contains(firstProcInst.getId()), is(true));
    assertThat(resultProcDefIds.contains(secondProcInst.getId()), is(true));
  }

  @Test
  public void internallyTerminatedCanceledInstancesOnlyFilter() throws Exception {
    //given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst = engineRule.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineRule.startProcessInstance(userTaskProcess.getId());
    engineRule.startProcessInstance(userTaskProcess.getId());

    engineDatabaseRule.changeProcessInstanceState(
            firstProcInst.getId(),
            CanceledInstancesOnlyQueryFilter.INTERNALLY_TERMINATED
    );
    engineDatabaseRule.changeProcessInstanceState(
            secondProcInst.getId(),
            CanceledInstancesOnlyQueryFilter.INTERNALLY_TERMINATED
    );

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData =
            ReportDataBuilderHelper.createProcessReportDataViewRawAsTable(userTaskProcess.getKey(), String.valueOf(userTaskProcess.getVersion()));
    reportData.setFilter(Collections.singletonList(new CanceledInstancesOnlyFilterDto()));
    RawDataProcessReportResultDto result = evaluateReport(reportData);

    //then
    assertThat(result.getResult().size(), is(2));
    List<String> resultProcDefIds = result.getResult()
            .stream()
            .map(RawDataProcessInstanceDto::getProcessInstanceId)
            .collect(Collectors.toList());

    assertThat(resultProcDefIds.contains(firstProcInst.getId()), is(true));
    assertThat(resultProcDefIds.contains(secondProcInst.getId()), is(true));
  }

  @Test
  public void externallyTerminatedCanceledInstncesOnlyFilter() {
    //given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst = engineRule.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineRule.startProcessInstance(userTaskProcess.getId());
    engineRule.startProcessInstance(userTaskProcess.getId());

    engineRule.externallyTerminateProcessInstance(firstProcInst.getId());
    engineRule.externallyTerminateProcessInstance(secondProcInst.getId());

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData =
            ReportDataBuilderHelper.createProcessReportDataViewRawAsTable(userTaskProcess.getKey(), String.valueOf(userTaskProcess.getVersion()));
    reportData.setFilter(Collections.singletonList(new CanceledInstancesOnlyFilterDto()));
    RawDataProcessReportResultDto result = evaluateReport(reportData);

    //then
    assertThat(result.getResult().size(), is(2));
    List<String> resultProcDefIds = result.getResult()
            .stream()
            .map(RawDataProcessInstanceDto::getProcessInstanceId)
            .collect(Collectors.toList());

    assertThat(resultProcDefIds.contains(firstProcInst.getId()), is(true));
    assertThat(resultProcDefIds.contains(secondProcInst.getId()), is(true));
  }

  private RawDataProcessReportResultDto evaluateReport(ProcessReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));
    return response.readEntity(RawDataProcessReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSingleUnsavedReportRequest(reportData)
            .execute();
  }

  private ProcessDefinitionEngineDto deployUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
            .name("aProcessName")
            .startEvent()
            .userTask()
            .endEvent()
            .done();
    return engineRule.deployProcessAndGetProcessDefinition(processModel);
  }
}
