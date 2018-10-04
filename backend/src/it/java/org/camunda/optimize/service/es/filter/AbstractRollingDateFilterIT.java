package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataSingleReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.junit.Rule;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_RAW_DATA_OPERATION;
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
      RawDataSingleReportResultDto result,
      int expectedPiCount
  ) {
    SingleReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat("rolling date result size", result.getResult().size(), is(expectedPiCount));

    if (expectedPiCount > 0) {
      RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
      assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    }
  }

  protected RawDataSingleReportResultDto createAndEvaluateReportWithRollingStartDateFilter(
      String processDefinitionKey,
      String processDefinitionVersion,
      String unit,
      boolean newToken
  ) {
    SingleReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    List<FilterDto> rollingDateFilter = DateUtilHelper.createRollingStartDateFilter(1L, unit);
    reportData.setFilter(rollingDateFilter);
    return evaluateReport(reportData, newToken);
  }

  protected RawDataSingleReportResultDto createAndEvaluateReportWithRollingEndDateFilter(
          String processDefinitionKey,
          String processDefinitionVersion,
          String unit,
          boolean newToken
  ) {
    SingleReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    List<FilterDto> rollingDateFilter = DateUtilHelper.createRollingEndDateFilter(1L, unit);
    reportData.setFilter(rollingDateFilter);
    return evaluateReport(reportData, newToken);
  }

  protected RawDataSingleReportResultDto evaluateReport(SingleReportDataDto reportData, boolean newToken) {
    Response response;
    if (newToken) {
      response = evaluateReportAndReturnResponseWithNewToken(reportData);
    } else {
      response = evaluateReportAndReturnResponse(reportData);
    }
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataSingleReportResultDto.class);
  }

  protected Response evaluateReportAndReturnResponse(SingleReportDataDto reportData) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSingleUnsavedReportRequest(reportData)
            .execute();
  }

  protected Response evaluateReportAndReturnResponseWithNewToken(SingleReportDataDto reportData) {
    String header = "Bearer " + embeddedOptimizeRule.getNewAuthenticationToken();
    return embeddedOptimizeRule
            .getRequestExecutor()
            .withGivenAuthHeader(header)
            .buildEvaluateSingleUnsavedReportRequest(reportData)
            .execute();
  }

}
