package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.camunda.optimize.test.util.ReportDataHelper;
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
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class RollingDateFilterIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);


  @Test
  public void rollingDateFilterInReport() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String processDefinitionId = processInstance.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    assertRollingFilter(processInstance, processDefinitionId, "days");

    assertRollingFilter(processInstance, processDefinitionId, "minutes");
    assertRollingFilter(processInstance, processDefinitionId, "hours");
    assertRollingFilter(processInstance, processDefinitionId, "weeks");
    assertRollingFilter(processInstance, processDefinitionId, "months");

    assertRollingFilter(processInstance, processDefinitionId, "nanos", 0);

  }

  @Test
  public void testRollingLogic() throws Exception {
    // given
    embeddedOptimizeRule.reloadConfiguration();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String processDefinitionId = processInstance.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    assertRollingFilter(processInstance, processDefinitionId, "days");

    //when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusDays(2));

    assertRollingFilter(processInstance, processDefinitionId, "days",0, true);

    embeddedOptimizeRule.reloadConfiguration();
    embeddedOptimizeRule.getNewAuthenticationToken();
  }

  private void assertRollingFilter(ProcessInstanceEngineDto processInstance, String processDefinitionId, String unit) {
    this.assertRollingFilter(processInstance, processDefinitionId, unit, 1, false);
  }

  private void assertRollingFilter(ProcessInstanceEngineDto processInstance, String processDefinitionId, String unit, int expectedSize) {
    this.assertRollingFilter(processInstance, processDefinitionId, unit, expectedSize, false);
  }

  private void assertRollingFilter(
      ProcessInstanceEngineDto processInstance,
      String processDefinitionId,
      String unit,
      int expectedSize,
      boolean newToken
  ) {
    // when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionId);
    List<FilterDto> rollingDateFilter = DateUtilHelper.createRollingDateFilter(1L, unit);
    reportData.setFilter(rollingDateFilter);
    RawDataReportResultDto result = evaluateReport(reportData, newToken);

    ReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat("[" + unit + "] rolling date result size", result.getResult().size(), is(expectedSize));

    if (expectedSize > 0) {
      RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
      assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    }
  }

  private RawDataReportResultDto evaluateReport(ReportDataDto reportData, boolean newToken) {
    Response response;
    if (newToken) {
      response = evaluateReportAndReturnResponseWithNewToken(reportData);
    } else {
      response = evaluateReportAndReturnResponse(reportData);
    }
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ReportDataDto reportData) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    return embeddedOptimizeRule.target("report/evaluate")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .post(Entity.json(reportData));
  }

  private Response evaluateReportAndReturnResponseWithNewToken(ReportDataDto reportData) {
    String token = embeddedOptimizeRule.getNewAuthenticationToken();
    return embeddedOptimizeRule.target("report/evaluate")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .post(Entity.json(reportData));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
        .name("aProcessName")
        .startEvent()
        .endEvent()
        .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }
}
