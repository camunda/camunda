package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.util.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataSingleReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.camunda.optimize.test.util.VariableFilterUtilHelper;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.IN;
import static org.camunda.optimize.test.util.ReportDataHelper.createReportDataViewRawAsTable;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class MixedFilterIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private final static String USER_TASK_ACTIVITY_ID = "userTask";

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private RawDataSingleReportResultDto evaluateReportWithFilter(ProcessDefinitionEngineDto processDefinition, List<FilterDto> filter) {
    SingleReportDataDto reportData =
      createReportDataViewRawAsTable(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
    reportData.setFilter(filter);
    return evaluateReport(reportData);
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


  @Test
  public void applyAllPossibleFilters() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");

      // this is the process instance that should be filtered
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), variables);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());

      // wrong not executed flow node
    engineRule.startProcessInstance(processDefinition.getId(), variables);

    // wrong variable
    variables.put("var", "anotherValue");
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), variables);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());

    // wrong date
    Thread.sleep(1000L);
    variables.put("var", "value");
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), variables);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    OffsetDateTime start = engineRule.getHistoricProcessInstance(instanceEngineDto.getId()).getStartTime();
    OffsetDateTime end = engineRule.getHistoricProcessInstance(instanceEngineDto.getId()).getEndTime();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<FilterDto> filterList = new ArrayList<>();

    VariableFilterDto filter = VariableFilterUtilHelper.createStringVariableFilter("var", IN, "value");
    filterList.add(filter);

    List<ExecutedFlowNodeFilterDto> flowNodeFilter = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .build();
    filterList.addAll(flowNodeFilter);
    filterList.addAll(DateUtilHelper.createFixedStartDateFilter(null , start.minusSeconds(1L)));
    filterList.addAll(DateUtilHelper.createFixedEndDateFilter(null , end.minusSeconds(1L)));
    RawDataSingleReportResultDto rawDataReportResultDto = evaluateReportWithFilter(processDefinition, filterList);

    // then
    assertThat(rawDataReportResultDto.getResult().size(), is(1));
  }


  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .userTask(USER_TASK_ACTIVITY_ID)
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }


}
