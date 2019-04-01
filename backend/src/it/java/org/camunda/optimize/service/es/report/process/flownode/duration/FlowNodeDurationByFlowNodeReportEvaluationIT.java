package org.camunda.optimize.service.es.report.process.flownode.duration;

import com.fasterxml.jackson.core.type.TypeReference;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;

@RunWith(JUnitParamsRunner.class)
public class FlowNodeDurationByFlowNodeReportEvaluationIT {

  public static final String PROCESS_DEFINITION_KEY = "123";
  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String SERVICE_TASK_ID = "aSimpleServiceTask";
  private static final String SERVICE_TASK_ID_2 = "aSimpleServiceTask2";
  private static final String USER_TASK = "userTask";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void reportEvaluationForOneProcess() throws Exception {

    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 20L);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    final ProcessDurationReportMapResultDto result = evaluationResponse.getResult();
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(String.valueOf(processDefinition.getVersion())));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.FLOW_NODE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(result.getData(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    assertThat(resultMap.get(SERVICE_TASK_ID), is(calculateExpectedValueGivenDurations(20L)));
    assertThat(resultMap.get(START_EVENT), is(calculateExpectedValueGivenDurations(20L)));
    assertThat(resultMap.get(END_EVENT), is(calculateExpectedValueGivenDurations(20L)));
  }

  @Test
  public void reportEvaluationForSeveralProcesses() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 10L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 30L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 20L);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    assertThat(resultMap.size(), is(3));
    assertThat(resultMap.get(SERVICE_TASK_ID), is(calculateExpectedValueGivenDurations(10L, 30L, 20L)));
  }

  @Test
  public void evaluateReportForMultipleEvents() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWithTwoTasks();

    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID, 100L);
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID_2, 20L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID, 200L);
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID_2, 10L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID, 900L);
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID_2, 90L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    assertThat(resultMap.size(), is(4));
    assertThat(resultMap.get(SERVICE_TASK_ID), is(calculateExpectedValueGivenDurations(100L, 200L, 900L)));
    assertThat(resultMap.get(SERVICE_TASK_ID_2), is(calculateExpectedValueGivenDurations(20L, 10L, 90L)));
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() throws SQLException {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployProcessWithTwoTasks();

    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID, 100L);
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID_2, 20L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID, 200L);
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID_2, 10L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID, 900L);
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID_2, 90L);


    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.ASC));
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    assertThat(resultMap.size(), is(4));
    assertThat(
      new ArrayList<>(resultMap.keySet()),
      // expect ascending order
      contains(new ArrayList<>(resultMap.keySet()).stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  private static Object[] aggregationTypes() {
    return AggregationType.values();
  }

  @Test
  @Parameters(method = "aggregationTypes")
  public void testCustomOrderOnResultValueIsApplied(final AggregationType aggregationType) throws SQLException {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWithTwoTasks();

    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID, 100L);
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID_2, 20L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID, 200L);
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID_2, 10L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID, 900L);
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID_2, 90L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.getConfiguration().setAggregationType(aggregationType);
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    assertThat(resultMap.size(), is(4));
    final List<Long> bucketValues = resultMap.values().stream()
      .map(bucketResult -> bucketResult.getResultForGivenAggregationType(aggregationType))
      .collect(Collectors.toList());
    assertThat(
      new ArrayList<>(bucketValues),
      contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  private ProcessDefinitionEngineDto deployProcessWithTwoTasks() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .serviceTask(SERVICE_TASK_ID)
      .camundaExpression("${true}")
      .serviceTask(SERVICE_TASK_ID_2)
      .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() throws Exception {
    //given
    ProcessDefinitionEngineDto firstDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessDefinitionEngineDto latestDefinition = deployProcessWithTwoTasks();
    assertThat(latestDefinition.getVersion(), is(2));

    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(firstDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 20L);
    processInstanceDto = engineRule.startProcessInstance(firstDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 30L);
    processInstanceDto = engineRule.startProcessInstance(latestDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 50L);
    processInstanceDto = engineRule.startProcessInstance(latestDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 120L);
    processInstanceDto = engineRule.startProcessInstance(latestDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 100L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    ProcessReportDataDto reportData = createFlowNodeDurationGroupByFlowNodeHeatmapReport(
      latestDefinition.getKey(), ReportConstants.ALL_VERSIONS
    );
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    //then
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    assertThat(resultMap.size(), is(4));
    assertThat(resultMap.get(SERVICE_TASK_ID), is(calculateExpectedValueGivenDurations(20L, 30L, 50L, 120L, 100L)));
    assertThat(resultMap.get(SERVICE_TASK_ID_2), is(calculateExpectedValueGivenDurations(50L, 120L, 100L)));
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() throws Exception {
    //given
    ProcessDefinitionEngineDto firstDefinition = deployProcessWithTwoTasks();
    ProcessDefinitionEngineDto latestDefinition = deploySimpleServiceTaskProcessDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(firstDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 20L);
    processInstanceDto = engineRule.startProcessInstance(firstDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 30L);
    processInstanceDto = engineRule.startProcessInstance(firstDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 50L);
    processInstanceDto = engineRule.startProcessInstance(latestDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 120L);
    processInstanceDto = engineRule.startProcessInstance(latestDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 100L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    ProcessReportDataDto reportData = createFlowNodeDurationGroupByFlowNodeHeatmapReport(
      latestDefinition.getKey(), ReportConstants.ALL_VERSIONS
    );
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    //then
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    assertThat(resultMap.size(), is(3));
    assertThat(resultMap.get(SERVICE_TASK_ID), is(calculateExpectedValueGivenDurations(20L, 30L, 50L, 120L, 100L)));
  }

  @Test
  public void reportAcrossAllVersions() throws Exception {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 10L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition2.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 20L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition2.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 90L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    ProcessReportDataDto reportData = createFlowNodeDurationGroupByFlowNodeHeatmapReport(
      processDefinition.getKey(), ReportConstants.ALL_VERSIONS
    );
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    //then
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    assertThat(resultMap.size(), is(3));
    assertThat(resultMap.get(SERVICE_TASK_ID), is(calculateExpectedValueGivenDurations(10L, 20L, 90L)));
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 80L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 40L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 120L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition2.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 20L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition2.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 100L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition2.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 1000L);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData1 = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse1 = evaluateReport(reportData1);
    ProcessReportDataDto reportData2 = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition2);
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse2 = evaluateReport(reportData2);

    // then
    Map<String, AggregationResultDto> resultMap = evaluationResponse1.getResult().getData();
    assertThat(resultMap.size(), is(3));
    assertThat(resultMap.get(SERVICE_TASK_ID), is(calculateExpectedValueGivenDurations(80L, 40L, 120L)));
    Map<String, AggregationResultDto> resultMap2 = evaluationResponse2.getResult().getData();
    assertThat(resultMap2.size(), is(3));
    assertThat(resultMap2.get(SERVICE_TASK_ID), is(calculateExpectedValueGivenDurations(20L, 100L, 1000L)));
  }

  @Test
  public void evaluateReportWithIrrationalAverageNumberAsResult() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 100L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 300L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 600L);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    assertThat(resultMap.size(), is(3));
    assertThat(resultMap.get(SERVICE_TASK_ID), is(calculateExpectedValueGivenDurations(100L, 300L, 600L)));
  }

  @Test
  public void noEventMatchesReturnsEmptyResult() {

    // when
    ProcessReportDataDto reportData =
      createFlowNodeDurationGroupByFlowNodeHeatmapReport("nonExistingProcessDefinitionId", "1");
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    assertThat(resultMap.size(), is(0));
  }

  @Test
  public void runningActivitiesAreNotConsidered() throws SQLException {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), START_EVENT, 100L);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createFlowNodeDurationGroupByFlowNodeHeatmapReport(
        processDefinition.getKey(),
        processDefinition.getVersionAsString()
      );
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.get(START_EVENT), is(calculateExpectedValueGivenDurations(100L)));
    assertThat(resultMap.get(USER_TASK), nullValue());
    assertThat(resultMap.get(END_EVENT), nullValue());
  }

  @Test
  public void processDefinitionContainsMultiInstanceBody() throws Exception {
    // given
    // @formatter:off
    BpmnModelInstance subProcess = Bpmn.createExecutableProcess("subProcess")
        .startEvent()
          .serviceTask(SERVICE_TASK_ID)
            .camundaExpression("${true}")
        .endEvent()
        .done();

    BpmnModelInstance miProcess = Bpmn.createExecutableProcess("miProcess")
        .name("MultiInstance")
          .startEvent("miStart")
          .callActivity("callActivity")
            .calledElement("subProcess")
            .camundaIn("activityDuration", "activityDuration")
            .multiInstance()
              .cardinality("2")
            .multiInstanceDone()
          .endEvent("miEnd")
        .done();
    // @formatter:on
    ProcessDefinitionEngineDto subProcessDefinition = engineRule.deployProcessAndGetProcessDefinition(subProcess);
    String processDefinitionId = engineRule.deployProcessAndGetId(miProcess);
    engineRule.startProcessInstance(processDefinitionId);
    engineDatabaseRule.changeActivityDurationForProcessDefinition(subProcessDefinition.getId(), 10L);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createFlowNodeDurationGroupByFlowNodeHeatmapReport(
        subProcessDefinition.getKey(),
        String.valueOf(subProcessDefinition.getVersion())
      );
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    assertThat(resultMap.size(), is(3));
    assertThat(resultMap.get(SERVICE_TASK_ID), is(calculateExpectedValueGivenDurations(10L)));
  }

  @Test
  public void evaluateReportForMoreThenTenEvents() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();

    ProcessInstanceEngineDto processInstanceDto;
    for (int i = 0; i < 11; i++) {
      processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
      engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 10L);
    }
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    assertThat(resultMap.size(), is(3));
    Long[] durationSet = new Long[11];
    Arrays.fill(durationSet, 10L);
    assertThat(resultMap.get(SERVICE_TASK_ID), is(calculateExpectedValueGivenDurations(durationSet)));
  }

  @Test
  public void filterInReport() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstance.getId(), 10L);
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, past.minusSeconds(1L)));
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    ProcessDurationReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertThat(resultMap.size(), is(0));

    // when
    reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.setFilter(createStartDateFilter(past, null));
    evaluationResponse = evaluateReport(reportData);

    // then
    result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    assertThat(resultMap.get(SERVICE_TASK_ID), is(calculateExpectedValueGivenDurations(10L)));
  }

  private List<ProcessFilterDto> createStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedStartDate().start(startDate).end(endDate).add().buildList();
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    ProcessReportDataDto dataDto =
      createFlowNodeDurationGroupByFlowNodeHeatmapReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setEntity(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto =
      createFlowNodeDurationGroupByFlowNodeHeatmapReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto =
      createFlowNodeDurationGroupByFlowNodeHeatmapReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .serviceTask(SERVICE_TASK_ID)
      .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessDefinitionEngineDto deploySimpleUserTaskDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK)
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluateReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto>>() {});
      // @formatter:on
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  private ProcessReportDataDto getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(ProcessDefinitionEngineDto processDefinition) {
    return createFlowNodeDurationGroupByFlowNodeHeatmapReport(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion())
    );
  }

  private AggregationResultDto calculateExpectedValueGivenDurations(final Long... setDuration) {
    final DescriptiveStatistics statistics = new DescriptiveStatistics();
    Stream.of(setDuration).map(Long::doubleValue).forEach(statistics::addValue);

    return new AggregationResultDto(
      Math.round(statistics.getMin()),
      Math.round(statistics.getMax()),
      Math.round(statistics.getMean()),
      Math.round(statistics.getPercentile(50.0D))
    );
  }

}
