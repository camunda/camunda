package org.camunda.optimize.service.es.report.process.user_task;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewOperation;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;


@RunWith(Parameterized.class)
public abstract class AbstractUserTaskDurationByUserTaskReportEvaluationIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String PROCESS_DEFINITION_KEY = "123";
  protected static final String USER_TASK_1 = "userTask1";
  protected static final String USER_TASK_2 = "userTask2";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  protected final ProcessViewOperation viewOperation;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {ProcessViewOperation.AVG}, {ProcessViewOperation.MIN}, {ProcessViewOperation.MAX}, {ProcessViewOperation.MEDIAN}
    });
  }

  public AbstractUserTaskDurationByUserTaskReportEvaluationIT(final ProcessViewOperation viewOperation) {
    this.viewOperation = viewOperation;
  }

  @Test
  public void reportEvaluationForOneProcess() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);

    final long setDuration = 20L;
    changeDuration(processInstanceDto, setDuration);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(String.valueOf(processDefinition.getVersion())));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(viewOperation));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.USER_TASK));
    assertThat(resultReportDataDto.getView().getProperty(), is(getViewProperty()));
    assertThat(result.getResult(), is(notNullValue()));

    final Map<String, Long> byUserTaskIdResult = result.getResult();
    assertThat(byUserTaskIdResult.size(), is(2));
    assertThat(byUserTaskIdResult.get(USER_TASK_1), is(calculateExpectedValueGivenDurations(setDuration)));
    assertThat(byUserTaskIdResult.get(USER_TASK_2), is(calculateExpectedValueGivenDurations(setDuration)));
  }

  @Test
  public void reportEvaluationForSeveralProcesses() throws Exception {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    final Long[] setDurations = new Long[]{10L, 30L};
    changeDuration(processInstanceDto1, setDurations[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, setDurations[1]);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData =
      createReport(processDefinition);
    final ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    final Map<String, Long> byUserTaskIdResult = result.getResult();
    assertThat(byUserTaskIdResult.size(), is(2));
    assertThat(byUserTaskIdResult.get(USER_TASK_1), is(calculateExpectedValueGivenDurations(setDurations)));
    assertThat(byUserTaskIdResult.get(USER_TASK_2), is(calculateExpectedValueGivenDurations(setDurations)));
  }

  @Test
  public void evaluateReportForMultipleEvents() throws Exception {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData =
      createReport(processDefinition);
    final ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    final Map<String, Long> byUserTaskIdResult = result.getResult();
    assertThat(byUserTaskIdResult.size(), is(2));
    assertThat(byUserTaskIdResult.get(USER_TASK_1), is(10L));
    assertThat(byUserTaskIdResult.get(USER_TASK_2), is(20L));
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() throws Exception {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(firstDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(latestDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final ProcessReportMapResultDto result = evaluateReport(reportData);

    //then
    final Map<String, Long> byUserTaskIdResult = result.getResult();
    assertThat(byUserTaskIdResult.size(), is(2));
    assertThat(byUserTaskIdResult.get(USER_TASK_1), is(calculateExpectedValueGivenDurations(20L, 40L)));
    assertThat(byUserTaskIdResult.get(USER_TASK_2), is(40L));
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() throws Exception {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(firstDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(latestDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final ProcessReportMapResultDto result = evaluateReport(reportData);

    //then
    final Map<String, Long> byUserTaskIdResult = result.getResult();
    assertThat(byUserTaskIdResult.size(), is(1));
    assertThat(byUserTaskIdResult.get(USER_TASK_1), is(calculateExpectedValueGivenDurations(20L, 40L)));
  }

  @Test
  public void reportAcrossAllVersions() throws Exception {
    //given
    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto processDefinition2 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition1.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, 40L);
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition2.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    final ProcessReportDataDto reportData = createReport(
      processDefinition1.getKey(), ReportConstants.ALL_VERSIONS
    );
    final ProcessReportMapResultDto result = evaluateReport(reportData);

    //then
    final Map<String, Long> byUserTaskIdResult = result.getResult();
    assertThat(byUserTaskIdResult.size(), is(1));
    assertThat(byUserTaskIdResult.get(USER_TASK_1), is(calculateExpectedValueGivenDurations(20L, 40L)));
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() throws Exception {
    // given
    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineRule.startProcessInstance(processDefinition1.getId());
    finishAllUserTasks(processInstanceDto1);
    changeDuration(processInstanceDto1, 40L);
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinition1.getId());
    finishAllUserTasks(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    final ProcessDefinitionEngineDto processDefinition2 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(processDefinition2.getId());
    finishAllUserTasks(processInstanceDto3);
    changeDuration(processInstanceDto3, 20L);
    final ProcessInstanceEngineDto processInstanceDto4 = engineRule.startProcessInstance(processDefinition2.getId());
    finishAllUserTasks(processInstanceDto4);
    changeDuration(processInstanceDto4, 20L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData1 = createReport(processDefinition1);
    final ProcessReportMapResultDto result1 = evaluateReport(reportData1);
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ProcessReportMapResultDto result2 = evaluateReport(reportData2);

    // then
    final Map<String, Long> byUserTaskIdResult1 = result1.getResult();
    assertThat(byUserTaskIdResult1.size(), is(1));
    assertThat(byUserTaskIdResult1.get(USER_TASK_1), is(40L));

    final Map<String, Long> byUserTaskIdResult2 = result2.getResult();
    assertThat(byUserTaskIdResult2.size(), is(1));
    assertThat(byUserTaskIdResult2.get(USER_TASK_1), is(20L));
  }

  @Test
  public void evaluateReportWithIrrationalAverageNumberAsResult() throws Exception {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);
    changeDuration(processInstanceDto, 100L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);
    changeDuration(processInstanceDto, 300L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);
    changeDuration(processInstanceDto, 600L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    final Map<String, Long> byUserTaskIdResult = result.getResult();
    assertThat(byUserTaskIdResult.size(), is(1));
    assertThat(byUserTaskIdResult.get(USER_TASK_1), is(calculateExpectedValueGivenDurations(100L, 300L, 600L)));
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    final Map<String, Long> byUserTaskIdResult = result.getResult();
    assertThat(byUserTaskIdResult.size(), is(0));
  }

  @Test
  public void runningUserTasksAreNotConsidered() throws Exception {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    // finish first task
    engineRule.finishAllUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, USER_TASK_1, 100L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    final Map<String, Long> byUserTaskIdResult = result.getResult();
    assertThat(byUserTaskIdResult.size(), is(1));
    assertThat(byUserTaskIdResult.get(USER_TASK_1), is(100L));
  }

  @Test
  public void processDefinitionContainsMultiInstanceBody() throws Exception {
    // given
    BpmnModelInstance processWithMultiInstanceUserTask = Bpmn
      // @formatter:off
        .createExecutableProcess("processWithMultiInstanceUserTask")
        .startEvent()
          .userTask(USER_TASK_1).multiInstance().cardinality("2").multiInstanceDone()
        .endEvent()
        .done();
    // @formatter:on

    final ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(
      processWithMultiInstanceUserTask
    );
    final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    final Map<String, Long> byUserTaskIdResult = result.getResult();
    assertThat(byUserTaskIdResult.size(), is(1));
    assertThat(byUserTaskIdResult.get(USER_TASK_1), is(10L));
  }

  @Test
  public void evaluateReportForMoreThenTenEvents() throws Exception {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();

    for (int i = 0; i < 11; i++) {
      final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
      engineRule.finishAllUserTasks(processInstanceDto.getId());
      changeDuration(processInstanceDto, 10L);
    }

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    final Map<String, Long> byUserTaskIdResult = result.getResult();
    assertThat(byUserTaskIdResult.size(), is(1));
    assertThat(byUserTaskIdResult.get(USER_TASK_1), is(10L));
  }

  @Test
  public void filterInReport() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    final OffsetDateTime processStartTime = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getStartTime();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, processStartTime.minusSeconds(1L)));
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> byUserTaskIdResult = result.getResult();
    assertThat(byUserTaskIdResult.size(), is(0));

    // when
    reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(processStartTime, null));
    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    byUserTaskIdResult = result.getResult();
    assertThat(byUserTaskIdResult.size(), is(1));
    assertThat(byUserTaskIdResult.get(USER_TASK_1 ), is(10L));
  }

  private List<ProcessFilterDto> createStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return DateUtilHelper.createFixedStartDateFilter(startDate, endDate);
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setEntity(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  protected abstract ProcessViewProperty getViewProperty();

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                         final String userTaskKey,
                                         final long duration);

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final long setDuration);

  protected abstract ProcessReportDataDto createReport(final String processDefinitionKey, final String version);

  private ProcessReportDataDto createReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private long calculateExpectedValueGivenDurations(final Long... setDuration) {
    final DescriptiveStatistics statistics = new DescriptiveStatistics();
    Stream.of(setDuration).map(Long::doubleValue).forEach(statistics::addValue);

    switch (viewOperation) {
      case AVG:
        return Math.round(statistics.getMean());
      case MIN:
        return Math.round(statistics.getMin());
      case MAX:
        return Math.round(statistics.getMax());
      case MEDIAN:
        return Math.round(statistics.getPercentile(50.0D));
      default:
        throw new RuntimeException("Unsupported viewOperation" + viewOperation);
    }
  }

  private void finishAllUserTasks(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish first task
    engineRule.finishAllUserTasks(processInstanceDto1.getId());
    // finish second task
    engineRule.finishAllUserTasks(processInstanceDto1.getId());
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .userTask(USER_TASK_2)
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessReportMapResultDto evaluateReport(ProcessReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(ProcessReportMapResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

}
