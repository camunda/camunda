package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewOperation;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
import org.junit.Rule;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;


public abstract class AbstractRollingDateFilterIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
        .name("aProcessName")
        .startEvent()
        .serviceTask()
        .camundaExpression("${true}")
        .userTask()
        .endEvent()
        .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected void assertResults(
      ProcessInstanceEngineDto processInstance,
      RawDataProcessReportResultDto result,
      int expectedPiCount
  ) {
    ProcessReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(ProcessViewOperation.RAW));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat("rolling date result size", result.getResult().size(), is(expectedPiCount));

    if (expectedPiCount > 0) {
      RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
      assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    }
  }

  protected RawDataProcessReportResultDto createAndEvaluateReportWithRollingStartDateFilter(
      String processDefinitionKey,
      String processDefinitionVersion,
      String unit,
      boolean newToken
  ) {
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    List<ProcessFilterDto> rollingDateFilter = DateUtilHelper.createRollingStartDateFilter(1L, unit);
    reportData.setFilter(rollingDateFilter);
    return evaluateReport(reportData, newToken);
  }

  protected RawDataProcessReportResultDto createAndEvaluateReportWithRollingEndDateFilter(
          String processDefinitionKey,
          String processDefinitionVersion,
          String unit,
          boolean newToken
  ) {
    ProcessReportDataDto reportData = ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    List<ProcessFilterDto> rollingDateFilter = DateUtilHelper.createRollingEndDateFilter(1L, unit);
    reportData.setFilter(rollingDateFilter);
    return evaluateReport(reportData, newToken);
  }

  protected RawDataProcessReportResultDto evaluateReport(ProcessReportDataDto reportData, boolean newToken) {
    Response response;
    if (newToken) {
      response = evaluateReportAndReturnResponseWithNewToken(reportData);
    } else {
      response = evaluateReportAndReturnResponse(reportData);
    }
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataProcessReportResultDto.class);
  }

  protected Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSingleUnsavedReportRequest(reportData)
            .execute();
  }

  protected Response evaluateReportAndReturnResponseWithNewToken(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .withGivenAuthToken(embeddedOptimizeRule.getNewAuthenticationToken())
            .buildEvaluateSingleUnsavedReportRequest(reportData)
            .execute();
  }

}
