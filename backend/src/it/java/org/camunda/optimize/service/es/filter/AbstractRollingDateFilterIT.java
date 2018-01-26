package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.junit.Rule;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_RAW_DATA_OPERATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 * @author Askar Akhmerov
 */
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
        .endEvent()
        .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected void assertResults(
      ProcessInstanceEngineDto processInstance,
      String processDefinitionId,
      RawDataReportResultDto result,
      int expectedPiCount
  ) {
    ReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat("rolling date result size", result.getResult().size(), is(expectedPiCount));

    if (expectedPiCount > 0) {
      RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
      assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    }
  }

  protected RawDataReportResultDto createAndEvaluateReport(String processDefinitionId, String unit, boolean newToken) {
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionId);
    List<FilterDto> rollingDateFilter = DateUtilHelper.createRollingDateFilter(1L, unit);
    reportData.setFilter(rollingDateFilter);
    return evaluateReport(reportData, newToken);
  }

  protected RawDataReportResultDto evaluateReport(ReportDataDto reportData, boolean newToken) {
    Response response;
    if (newToken) {
      response = evaluateReportAndReturnResponseWithNewToken(reportData);
    } else {
      response = evaluateReportAndReturnResponse(reportData);
    }
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataReportResultDto.class);
  }

  protected Response evaluateReportAndReturnResponse(ReportDataDto reportData) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    return embeddedOptimizeRule.target("report/evaluate")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .post(Entity.json(reportData));
  }

  protected Response evaluateReportAndReturnResponseWithNewToken(ReportDataDto reportData) {
    String token = embeddedOptimizeRule.getNewAuthenticationToken();
    return embeddedOptimizeRule.target("report/evaluate")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .post(Entity.json(reportData));
  }

}
