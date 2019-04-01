package org.camunda.optimize.service.es.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.IN;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable;
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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filterList = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(USER_TASK_ACTIVITY_ID)
      .add()
      .variable()
      .stringType()
      .values(Collections.singletonList("value"))
      .name("var")
      .operator(IN)
      .add()
      .fixedStartDate()
      .start(null)
      .end(start.minusSeconds(1L))
      .add()
      .fixedEndDate()
      .start(null)
      .end(end.minusSeconds(1L))
      .add()
      .buildList();

    RawDataProcessReportResultDto rawDataReportResultDto = evaluateReportWithFilter(processDefinition, filterList);

    // then
    assertThat(rawDataReportResultDto.getData().size(), is(1));
  }

  private RawDataProcessReportResultDto evaluateReportWithFilter(ProcessDefinitionEngineDto processDefinition, List<ProcessFilterDto> filter) {
    ProcessReportDataDto reportData =
      createProcessReportDataViewRawAsTable(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
    reportData.setFilter(filter);
    return evaluateReport(reportData).getResult();
  }

  private ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateReport(final ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {});
      // @formatter:on
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .userTask(USER_TASK_ACTIVITY_ID)
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

}
