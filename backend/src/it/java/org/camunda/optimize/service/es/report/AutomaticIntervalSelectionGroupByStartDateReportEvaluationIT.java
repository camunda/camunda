package org.camunda.optimize.service.es.report;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportUtil.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByStartDate;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;


public class AutomaticIntervalSelectionGroupByStartDateReportEvaluationIT {

  private EngineIntegrationRule engineRule = new EngineIntegrationRule();
  private ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  private EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  private EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();


  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void automaticIntervalSelectionWorks() throws SQLException {
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(processInstanceDto1.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto1.getDefinitionId());
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday);
    updates.put(processInstanceDto3.getId(), startOfToday.minusDays(1));
    engineDatabaseRule.updateProcessInstanceStartDates(updates);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createCountProcessInstanceFrequencyGroupByStartDate(
        processInstanceDto1.getProcessDefinitionKey(),
        processInstanceDto1.getProcessDefinitionVersion(),
        GroupByDateUnit.AUTOMATIC
      );
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> resultMap = result.getResult();
    List<Long> resultValues = new ArrayList<>(resultMap.values());
    assertThat(resultMap.size(), is(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
    assertThat(resultValues.get(0), is(2L));
    assertThat(resultValues.get(resultMap.size() - 1), is(1L));
  }

  @Test
  public void automaticIntervalSelectionTakesAllProcessInstancesIntoAccount() throws SQLException {
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(processInstanceDto1.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto1.getDefinitionId());
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday.plusDays(2));
    updates.put(processInstanceDto3.getId(), startOfToday.plusDays(5));
    engineDatabaseRule.updateProcessInstanceStartDates(updates);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createCountProcessInstanceFrequencyGroupByStartDate(
        processInstanceDto1.getProcessDefinitionKey(),
        processInstanceDto1.getProcessDefinitionVersion(),
        GroupByDateUnit.AUTOMATIC
      );
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> resultMap = result.getResult();
    List<Long> resultValues = new ArrayList<>(resultMap.values());
    assertThat(resultMap.size(), is(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
    assertThat(resultValues.stream().mapToInt(Long::intValue).sum(), is(3));
    assertThat(resultValues.get(0), is(1L));
    assertThat(resultValues.get(resultMap.size() - 1), is(1L));
  }

  @Test
  public void automaticIntervalSelectionForNoData() {
    ProcessDefinitionEngineDto engineDto = deploySimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createCountProcessInstanceFrequencyGroupByStartDate(
        engineDto.getKey(),
        engineDto.getVersionAsString(),
        GroupByDateUnit.AUTOMATIC
      );
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.size(), is(0));
  }

  @Test
  public void automaticIntervalSelectionForOneDataPoint() {
    // given there is only one data point
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createCountProcessInstanceFrequencyGroupByStartDate(
        engineDto.getProcessDefinitionKey(),
        engineDto.getProcessDefinitionVersion(),
        GroupByDateUnit.AUTOMATIC
      );
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then the single data point should be grouped by month
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.size(), is(1));
    OffsetDateTime nowStrippedToMonth =
      OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1).withHour(1);
    String nowStrippedToMonthAsString = localDateTimeToString(nowStrippedToMonth);
    assertThat(resultMap.get(nowStrippedToMonthAsString), is(1L));
  }

  @Test
  public void combinedReportsWithDistinctRanges() throws Exception {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefFirstRange = startProcessInstancesInDayRange(now.plusDays(1), now.plusDays(3));
    ProcessDefinitionEngineDto procDefSecondRange = startProcessInstancesInDayRange(now.plusDays(4), now.plusDays(6));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    String singleReportId = createNewSingleReport(procDefFirstRange);
    String singleReportId2 = createNewSingleReport(procDefSecondRange);

    // when
    CombinedProcessReportResultDto result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, ProcessReportMapResultDto> resultMap = result.getResult();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  @Test
  public void combinedReportsWithOneIncludingRange() throws Exception {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefFirstRange = startProcessInstancesInDayRange(now.plusDays(1), now.plusDays(6));
    ProcessDefinitionEngineDto procDefSecondRange = startProcessInstancesInDayRange(now.plusDays(3), now.plusDays(5));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    String singleReportId = createNewSingleReport(procDefFirstRange);
    String singleReportId2 = createNewSingleReport(procDefSecondRange);

    // when
    CombinedProcessReportResultDto result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, ProcessReportMapResultDto> resultMap = result.getResult();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  @Test
  public void combinedReportsWithIntersectingRange() throws Exception {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefFirstRange = startProcessInstancesInDayRange(now.plusDays(1), now.plusDays(4));
    ProcessDefinitionEngineDto procDefSecondRange = startProcessInstancesInDayRange(now.plusDays(3), now.plusDays(6));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    String singleReportId = createNewSingleReport(procDefFirstRange);
    String singleReportId2 = createNewSingleReport(procDefSecondRange);

    // when
    CombinedProcessReportResultDto result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, ProcessReportMapResultDto> resultMap = result.getResult();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  private void assertResultIsInCorrectRanges(OffsetDateTime startRange,
                                             OffsetDateTime endRange,
                                             Map<String,ProcessReportMapResultDto> resultMap,
                                             int resultSize) {
    assertThat(resultMap.size(), is(resultSize));
    for (ProcessReportMapResultDto result : resultMap.values()) {
      Map<String, Long> singleProcessResult = result.getResult();
      assertThat(singleProcessResult.size(), is(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
      LinkedList<String> strings = new LinkedList<>(singleProcessResult.keySet());
      assertThat(strings.getLast(), is(localDateTimeToString(startRange)));
      assertIsInRangeOfLastInterval(strings.getFirst(), startRange, endRange);
    }
  }

  private void assertIsInRangeOfLastInterval(String lastIntervalAsString,
                                             OffsetDateTime startTotal,
                                             OffsetDateTime endTotal) {
    long totalDuration = endTotal.toInstant().toEpochMilli() - startTotal.toInstant().toEpochMilli();
    long interval = totalDuration / NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
    assertThat(
      lastIntervalAsString,
      greaterThanOrEqualTo(localDateTimeToString(endTotal.minus(interval, ChronoUnit.MILLIS)))
    );
    assertThat(lastIntervalAsString, lessThan(localDateTimeToString(endTotal)));
  }

  private String createNewSingleReport(ProcessDefinitionEngineDto engineDto) {
    String singleReportId = createNewSingleReport();
    ProcessReportDataDto reportDataDto =
      createCountProcessInstanceFrequencyGroupByStartDate(
        engineDto.getKey(),
        engineDto.getVersionAsString(),
        GroupByDateUnit.AUTOMATIC
      );
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setData(reportDataDto);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private String createNewSingleReport() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateSingleProcessReportRequest()
            .execute(IdDto.class, 200)
            .getId();
  }

  private CombinedProcessReportResultDto evaluateUnsavedCombined(CombinedReportDataDto reportDataDto) {
    Response response = evaluateUnsavedCombinedReportAndReturnResponse(reportDataDto);

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    return response.readEntity(CombinedProcessReportResultDto.class);
  }

  private Response evaluateUnsavedCombinedReportAndReturnResponse(CombinedReportDataDto reportDataDto) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
            .execute();
  }

  private ProcessDefinitionEngineDto startProcessInstancesInDayRange(OffsetDateTime min,
                                                                     OffsetDateTime max) throws SQLException {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    ProcessInstanceEngineDto procInstMin = engineRule.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto procInstMax = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeProcessInstanceStartDate(procInstMin.getId(), min);
    engineDatabaseRule.changeProcessInstanceStartDate(procInstMax.getId(), max);
    return processDefinition;
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
    processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    return processInstanceEngineDto;
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

  private String localDateTimeToString(OffsetDateTime time) {
    return embeddedOptimizeRule.getDateTimeFormatter().format(time);
  }

}
