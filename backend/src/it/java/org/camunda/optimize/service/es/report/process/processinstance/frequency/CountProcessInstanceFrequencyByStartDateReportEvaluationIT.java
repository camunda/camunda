package org.camunda.optimize.service.es.report.process.processinstance.frequency;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
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

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByStartDate;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;


public class CountProcessInstanceFrequencyByStartDateReportEvaluationIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();


  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void simpleReportEvaluation() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.START_DATE));
    StartDateGroupByDto startDateGroupByDto = (StartDateGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(startDateGroupByDto.getValue().getUnit(), is(GroupByDateUnit.DAY));

    final ProcessReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    Map<String, Long> resultMap = result.getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultMap.containsKey(localDateTimeToString(startOfToday)), is(true));
    assertThat(resultMap.containsValue(1L), is(true));
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    String reportId = createAndStoreDefaultReportDefinition(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );

    // when
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluationResponse = evaluateReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.START_DATE));
    StartDateGroupByDto startDateGroupByDto = (StartDateGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(startDateGroupByDto.getValue().getUnit(), is(GroupByDateUnit.DAY));

    final ProcessReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    Map<String, Long> resultMap = result.getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultMap.containsKey(localDateTimeToString(startOfToday)), is(true));
    assertThat(resultMap.containsValue(1L), is(true));
  }

  @Test
  public void resultIsSortedInDescendingOrder() throws Exception {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    final String definitionId = processInstanceDto.getDefinitionId();
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), OffsetDateTime.now().minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(1));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    final ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    assertThat(
      new ArrayList<>(resultMap.keySet()),
      // expect ascending order
      contains(new ArrayList<>(resultMap.keySet()).stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() throws SQLException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    final String definitionId = processInstanceDto.getDefinitionId();
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), OffsetDateTime.now().minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(1));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    assertThat(
      new ArrayList<>(resultMap.keySet()),
      // expect ascending order
      contains(new ArrayList<>(resultMap.keySet()).stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() throws SQLException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    final String definitionId = processInstanceDto.getDefinitionId();
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), OffsetDateTime.now().minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto4 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto4.getId(), OffsetDateTime.now().minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto5 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto5.getId(), OffsetDateTime.now().minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto6 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto6.getId(), OffsetDateTime.now().minusDays(2));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    final List<Long> bucketValues = new ArrayList<>(resultMap.values());
    assertThat(
      new ArrayList<>(bucketValues),
      contains(bucketValues.stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }

  @Test
  public void processInstancesStartedAtSameIntervalAreGroupedTogether() throws Exception {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(1));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(2));
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    String expectedStringToday = localDateTimeToString(startOfToday);
    assertThat(resultMap.containsKey(expectedStringToday), is(true));
    assertThat(resultMap.get(expectedStringToday), is(2L));
    String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultMap.containsKey(expectedStringYesterday), is(true));
    assertThat(resultMap.get(expectedStringYesterday), is(1L));
  }

  @Test
  public void emptyIntervalBetweenTwoProcessInstances() throws Exception {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(2));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    String expectedStringToday = localDateTimeToString(startOfToday);
    assertThat(resultMap.containsKey(expectedStringToday), is(true));
    assertThat(resultMap.get(expectedStringToday), is(2L));
    String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultMap.containsKey(expectedStringYesterday), is(true));
    assertThat(resultMap.get(expectedStringYesterday), is(0L));
    String expectedStringDayBeforeYesterday = localDateTimeToString(startOfToday.minusDays(2));
    assertThat(resultMap.containsKey(expectedStringDayBeforeYesterday), is(true));
    assertThat(resultMap.get(expectedStringDayBeforeYesterday), is(1L));
  }

  @Test
  public void countGroupedByHour() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(5);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesStartTime(processInstanceDtos, now, ChronoUnit.HOURS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion(),
      GroupByDateUnit.HOUR
    );
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> resultMap = result.getData();
    assertStartDateResultMap(resultMap, 5, now, ChronoUnit.HOURS);
  }

  private void assertStartDateResultMap(Map<String, Long> resultMap, int size, OffsetDateTime now, ChronoUnit unit) {
    assertThat(resultMap.size(), is(size));
    final ZonedDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit);
    IntStream.range(0, size)
      .forEach(i -> {
        String expectedDateString = localDateTimeToString(finalStartOfUnit.minus((i), unit));
        assertThat("contains [" + expectedDateString + "]", resultMap.containsKey(expectedDateString), is(true));
        assertThat(resultMap.get(expectedDateString), is(1L));
      });
  }

  private void updateProcessInstancesStartTime(List<ProcessInstanceEngineDto> procInsts,
                                               OffsetDateTime now,
                                               ChronoUnit unit) throws SQLException {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    IntStream.range(0, procInsts.size())
      .forEach(i -> {
        String id = procInsts.get(i).getId();
        OffsetDateTime newStartDate = now.minus(i, unit);
        idToNewStartDate.put(id, newStartDate);
      });
    engineDatabaseRule.updateProcessInstanceStartDates(idToNewStartDate);
  }

  @Test
  public void countGroupedByDay() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesStartTime(processInstanceDtos, now, ChronoUnit.DAYS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> resultMap = result.getData();
    assertStartDateResultMap(resultMap, 8, now, ChronoUnit.DAYS);
  }

  @Test
  public void countGroupedByWeek() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesStartTime(processInstanceDtos, now, ChronoUnit.WEEKS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion(),
      GroupByDateUnit.WEEK
    );
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> resultMap = result.getData();
    assertStartDateResultMap(resultMap, 8, now, ChronoUnit.WEEKS);
  }

  @Test
  public void countGroupedByMonth() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(3);
    OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updateProcessInstancesStartTime(processInstanceDtos, now, ChronoUnit.MONTHS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion(),
      GroupByDateUnit.MONTH
    );
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> resultMap = result.getData();
    assertStartDateResultMap(resultMap, 3, now, ChronoUnit.MONTHS);
  }

  @Test
  public void countGroupedByYear() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesStartTime(processInstanceDtos, now, ChronoUnit.YEARS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion(),
      GroupByDateUnit.YEAR
    );
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> resultMap = result.getData();
    assertStartDateResultMap(resultMap, 8, now, ChronoUnit.YEARS);
  }

  @Test
  public void reportAcrossAllVersions() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(), ReportConstants.ALL_VERSIONS, GroupByDateUnit.DAY
    );
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    Map<String, Long> resultMap = result.getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    String expectedStartDateString = localDateTimeToString(startOfToday);
    assertThat("contains [" + expectedStartDateString + "]", resultMap.containsKey(expectedStartDateString), is(true));
    assertThat(resultMap.get(expectedStartDateString), is(2L));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    Map<String, Long> resultMap = result.getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    String expectedStartDateString = localDateTimeToString(startOfToday);
    assertThat("contains [" + expectedStartDateString + "]", resultMap.containsKey(expectedStartDateString), is(true));
    assertThat(resultMap.get(expectedStartDateString), is(1L));
  }

  @Test
  public void flowNodeFilterInReport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("goToTask1", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), GroupByDateUnit.DAY
    );

    List<ProcessFilterDto> flowNodeFilter = ProcessFilterBuilder.filter().executedFlowNodes()
          .id("task1")
          .add()
          .buildList();

    reportData.getFilter().addAll(flowNodeFilter);
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto =
      createCountProcessInstanceFrequencyGroupByStartDate("123", "1", GroupByDateUnit.DAY);
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  public void optimizeExceptionOnGroupByUnitIsNull() {
    // given
    ProcessReportDataDto dataDto = createCountProcessInstanceFrequencyGroupByStartDate("123", "1", GroupByDateUnit.DAY);
    StartDateGroupByDto groupByDto = (StartDateGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setUnit(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleProcesses(1).get(0);
  }

  private List<ProcessInstanceEngineDto> deployAndStartSimpleProcesses(int number) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    return IntStream.range(0, number)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(processModel);
  }

  private ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent("startEvent")
      .exclusiveGateway("splittingGateway")
      .name("Should we go to task 1?")
      .condition("yes", "${goToTask1}")
      .serviceTask("task1")
      .camundaExpression("${true}")
      .exclusiveGateway("mergeGateway")
      .endEvent("endEvent")
      .moveToNode("splittingGateway")
      .condition("no", "${!goToTask1}")
      .serviceTask("task2")
      .camundaExpression("${true}")
      .connectTo("mergeGateway")
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }


  private String createAndStoreDefaultReportDefinition(String processDefinitionKey, String processDefinitionVersion) {
    String id = createNewReport();
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processDefinitionKey, processDefinitionVersion, GroupByDateUnit.DAY
    );
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setId(id);
    report.setLastModifier("something");
    report.setName("something");
    report.setCreated(OffsetDateTime.now());
    report.setLastModified(OffsetDateTime.now());
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }

  private String createNewReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluateReportById(String reportId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessReportMapResultDto>>() {});
      // @formatter:on
  }

  private ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluateReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessReportMapResultDto>>() {});
      // @formatter:on
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  private String localDateTimeToString(ZonedDateTime time) {
    return embeddedOptimizeRule.getDateTimeFormatter().format(time);
  }

}
