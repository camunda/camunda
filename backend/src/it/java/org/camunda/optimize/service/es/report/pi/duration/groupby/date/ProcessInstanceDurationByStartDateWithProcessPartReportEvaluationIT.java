package org.camunda.optimize.service.es.report.pi.duration.groupby.date;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.MapProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewOperation;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataBuilder;
import org.camunda.optimize.test.util.ReportDataType;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.camunda.optimize.test.util.ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART;
import static org.camunda.optimize.test.util.ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART;
import static org.camunda.optimize.test.util.ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART;
import static org.camunda.optimize.test.util.ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART;
import static org.camunda.optimize.test.util.VariableFilterUtilHelper.createBooleanVariableFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public class ProcessInstanceDurationByStartDateWithProcessPartReportEvaluationIT {

  private static final String END_EVENT = "endEvent";
  private static final String START_EVENT = "startEvent";
  private static final String START_LOOP = "mergeExclusiveGateway";
  private static final String END_LOOP = "splittingGateway";
  private static final String TEST_ACTIVITY = "testActivity";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

  @Test
  @Parameters
  public void reportEvaluationForOneProcess(ReportDataType reportDataType, ProcessViewOperation operation) throws Exception {

    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    OffsetDateTime endDate = activityStartDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      activityStartDate
    );
    engineDatabaseRule.changeActivityInstanceEndDateForProcessDefinition(processInstanceDto.getDefinitionId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setDateInterval(GroupByDateUnit.DAY)
            .setReportDataType(reportDataType)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.START_DATE));
    assertThat(resultReportDataDto.getParameters().getProcessPart(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    OffsetDateTime startOfToday = new Date().toInstant().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    assertThat(resultMap.containsKey(localDateTimeToString(startOfToday)), is(true));
    assertThat(resultMap.get(localDateTimeToString(startOfToday)), is(1000L));
  }

  private Object[] parametersForReportEvaluationForOneProcess() {
    return new Object[]{
      new Object[]{AVG_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, ProcessViewOperation.AVG},
      new Object[]{MIN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, ProcessViewOperation.MIN},
      new Object[]{MAX_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, ProcessViewOperation.MAX},
      new Object[]{MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, ProcessViewOperation.MEDIAN}
    };
  }

  @Test
  @Parameters
  public void reportEvaluationById(ReportDataType reportDataType, ProcessViewOperation operation) throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    OffsetDateTime endDate = activityStartDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      activityStartDate
    );
    engineDatabaseRule.changeActivityInstanceEndDateForProcessDefinition(processInstanceDto.getDefinitionId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    ProcessReportDataDto reportDataDto = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .setDateInterval(GroupByDateUnit.DAY)
            .build();

    String reportId = createAndStoreDefaultReportDefinition(reportDataDto);

    // when
    MapProcessReportResultDto result = evaluateReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));

    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.START_DATE));
    assertThat(resultReportDataDto.getParameters().getProcessPart(), is(notNullValue()));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    OffsetDateTime startOfToday = new Date().toInstant().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    assertThat(resultMap.containsKey(localDateTimeToString(startOfToday)), is(true));
    assertThat(resultMap.get(localDateTimeToString(startOfToday)), is(1000L));
  }

  private Object[] parametersForReportEvaluationById() {
    return new Object[]{
      new Object[]{AVG_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, ProcessViewOperation.AVG},
      new Object[]{MIN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, ProcessViewOperation.MIN},
      new Object[]{MAX_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, ProcessViewOperation.MAX},
      new Object[]{MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, ProcessViewOperation.MEDIAN}
    };
  }

  @Test
  @Parameters
  public void evaluateReportForMultipleEvents(ReportDataType reportDataType, Long expectedDuration) throws
                                                                                                          Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstStartDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(procDefDto.getKey())
            .setProcessDefinitionVersion(procDefDto.getVersionAsString())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setDateInterval(GroupByDateUnit.DAY)
            .setReportDataType(reportDataType)
            .build();
    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    OffsetDateTime startOfToday = new Date().toInstant().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    assertThat(resultMap.containsKey(localDateTimeToString(startOfToday)), is(true));
    assertThat(resultMap.get(localDateTimeToString(startOfToday)), is(expectedDuration));
  }

  private Object[] parametersForEvaluateReportForMultipleEvents() {
    return new Object[]{
      new Object[]{AVG_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 4000L},
      new Object[]{MIN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 1000L},
      new Object[]{MAX_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 9000L},
      new Object[]{MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 2000L}
    };
  }

  @Test
  @Parameters
  public void multipleEventsInEachDateRange(ReportDataType reportDataType,
                                            Long expectedTodaysDuration,
                                            Long expectedYesterdaysDuration) throws
                                                                             Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstStartDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    startThreeProcessInstances(procInstStartDate, -1, procDefDto, Arrays.asList(2, 4, 12));


    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(procDefDto.getKey())
            .setProcessDefinitionVersion(procDefDto.getVersionAsString())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .setDateInterval(GroupByDateUnit.DAY)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.size(), is(2));
    OffsetDateTime startOfToday = procInstStartDate.truncatedTo(ChronoUnit.DAYS);
    String expectedStringToday = localDateTimeToString(startOfToday);
    assertThat(resultMap.containsKey(expectedStringToday), is(true));
    assertThat(resultMap.get(expectedStringToday), is(expectedTodaysDuration));
    String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultMap.containsKey(expectedStringYesterday), is(true));
    assertThat(resultMap.get(expectedStringYesterday), is(expectedYesterdaysDuration));
  }

  private Object[] parametersForMultipleEventsInEachDateRange() {
    return new Object[]{
      new Object[]{AVG_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 4000L, 6000L},
      new Object[]{MIN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 1000L, 2000L},
      new Object[]{MAX_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 9000L, 12000L},
      new Object[]{MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 2000L, 4000L}
    };
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void takeCorrectActivityOccurrences(ReportDataType reportDataType) throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    OffsetDateTime activityStartDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartLoopingProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeFirstActivityInstanceStartDate(START_LOOP, activityStartDate);
    engineDatabaseRule.changeFirstActivityInstanceEndDate(END_LOOP, activityStartDate.plusSeconds(2));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_LOOP)
            .setEndFlowNodeId(END_LOOP)
            .setReportDataType(reportDataType)
            .setDateInterval(GroupByDateUnit.DAY)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    OffsetDateTime startOfToday = new Date().toInstant().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    assertThat(resultMap.containsKey(localDateTimeToString(startOfToday)), is(true));
    assertThat(resultMap.get(localDateTimeToString(startOfToday)), is(2000L));
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void unknownStartReturnsEmptyResult(ReportDataType reportDataType) throws SQLException {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceEndDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().plusHours(1)
    );
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId("foo")
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .setDateInterval(GroupByDateUnit.DAY)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().isEmpty(), is(true));
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void unknownEndReturnsEmptyResult(ReportDataType reportDataType) throws SQLException {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().minusHours(1)
    );
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId("foo")
            .setReportDataType(reportDataType)
            .setDateInterval(GroupByDateUnit.DAY)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().isEmpty(), is(true));
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void noAvailableProcessInstancesReturnsEmptyResult(ReportDataType reportDataType) {
    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey("fooProcDef")
            .setProcessDefinitionVersion("1")
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .setDateInterval(GroupByDateUnit.DAY)
            .build();
    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().isEmpty(), is(true));
  }

  @Test
  @Parameters
  public void reportAcrossAllVersions(ReportDataType reportDataType, Long expectedDuration) throws Exception {
    //given
    OffsetDateTime procInstStartDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(1));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(9));
    processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(2));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .setDateInterval(GroupByDateUnit.DAY)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    OffsetDateTime startOfToday = new Date().toInstant().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    assertThat(resultMap.containsKey(localDateTimeToString(startOfToday)), is(true));
    assertThat(resultMap.get(localDateTimeToString(startOfToday)), is(expectedDuration));
  }

  private Object[] parametersForReportAcrossAllVersions() {
    return new Object[]{
      new Object[]{AVG_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 4000L},
      new Object[]{MIN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 1000L},
      new Object[]{MAX_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 9000L},
      new Object[]{MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 2000L}
    };
  }

  @Test
  @Parameters
  public void otherProcessDefinitionsDoNoAffectResult(ReportDataType reportDataType,
                                                      Long expectedDuration) throws
                                                                             Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    OffsetDateTime activityStartdate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartdate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartdate.plusSeconds(1));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartdate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartdate.plusSeconds(9));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartdate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartdate.plusSeconds(2));
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processDefinitionKey)
            .setProcessDefinitionVersion(processDefinitionVersion)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .setDateInterval(GroupByDateUnit.DAY)
            .build();
    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    OffsetDateTime startOfToday = new Date().toInstant().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    assertThat(resultMap.containsKey(localDateTimeToString(startOfToday)), is(true));
    assertThat(resultMap.get(localDateTimeToString(startOfToday)), is(expectedDuration));
  }

  private Object[] parametersForOtherProcessDefinitionsDoNoAffectResult() {
    return new Object[]{
      new Object[]{AVG_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 4000L},
      new Object[]{MIN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 1000L},
      new Object[]{MAX_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 9000L},
      new Object[]{MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 2000L}
    };
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void filterInReportWorks(ReportDataType reportDataType) throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(1));
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setDateInterval(GroupByDateUnit.DAY)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setFilter(createVariableFilter("true"))
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> resultMap = result.getResult();
    OffsetDateTime startOfToday = new Date().toInstant().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    assertThat(resultMap.containsKey(localDateTimeToString(startOfToday)), is(true));
    assertThat(resultMap.get(localDateTimeToString(startOfToday)), is(1000L));

    // when
    reportData.setFilter(createVariableFilter("false"));
    result = evaluateReport(reportData);

    // then
    resultMap = result.getResult();
    assertThat(resultMap.isEmpty(), is(true));
  }

  @Test
  @Parameters
  public void processInstancesStartedAtSameIntervalAreGroupedTogether(ReportDataType reportDataType,
                                                                      long expectedToday,
                                                                      long expectedYesterday) throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(1));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(2));
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(9));
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto3.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto3.getId(), activityStartDate.plusSeconds(1));
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstStartDate, -1L);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setDateInterval(GroupByDateUnit.DAY)
            .setProcessDefinitionKey(processDefinitionKey)
            .setProcessDefinitionVersion(processDefinitionVersion)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.size(), is(2));
    OffsetDateTime startOfToday = procInstStartDate.truncatedTo(ChronoUnit.DAYS);
    String expectedStringToday = localDateTimeToString(startOfToday);
    assertThat(resultMap.containsKey(expectedStringToday), is(true));
    assertThat(resultMap.get(expectedStringToday), is(expectedToday));
    String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultMap.containsKey(expectedStringYesterday), is(true));
    assertThat(resultMap.get(expectedStringYesterday), is(expectedYesterday));
  }

  private Object[] parametersForProcessInstancesStartedAtSameIntervalAreGroupedTogether() {
    return new Object[]{
      new Object[]{AVG_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 4000L, 1000L},
      new Object[]{MIN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 1000L, 1000L},
      new Object[]{MAX_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 9000L, 1000L},
      new Object[]{MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 2000L, 1000L}
    };
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void resultIsSortedInDescendingOrder(ReportDataType reportDataType) throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, -2L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstStartDate, -1L);


    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setDateInterval(GroupByDateUnit.DAY)
            .setProcessDefinitionKey(processDefinitionKey)
            .setProcessDefinitionVersion(processDefinitionVersion)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.size(), is(3));
    assertThat(new ArrayList<>(resultMap.keySet()), isInDescendingOrdering());
  }

  private Matcher<? super List<String>> isInDescendingOrdering() {
    return new TypeSafeMatcher<List<String>>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("The given list should be sorted in descending order!");
      }

      @Override
      protected boolean matchesSafely(List<String> item) {
        for (int i = (item.size() - 1); i > 0; i--) {
          if (item.get(i).compareTo(item.get(i - 1)) > 0) {
            return false;
          }
        }
        return true;
      }
    };
  }

  @Test
  @Parameters
  public void emptyIntervalBetweenTwoProcessInstances(ReportDataType reportDataType,
                                                      long expectedTodayDuration) throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstStartDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstStartDate, -2L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto3.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto3.getId(), activityStartDate.plusSeconds(2));

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setDateInterval(GroupByDateUnit.DAY)
            .setProcessDefinitionKey(procDefDto.getKey())
            .setProcessDefinitionVersion(procDefDto.getVersionAsString())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.size(), is(3));

    OffsetDateTime startOfToday = new Date().toInstant().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);

    String expectedStringToday = localDateTimeToString(startOfToday);
    assertThat(resultMap.containsKey(expectedStringToday), is(true));
    assertThat(resultMap.get(expectedStringToday), is(expectedTodayDuration));
    String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultMap.containsKey(expectedStringYesterday), is(true));
    assertThat(resultMap.get(expectedStringYesterday), is(0L));
    String expectedStringDayBeforeYesterday = localDateTimeToString(startOfToday.minusDays(2));
    assertThat(resultMap.containsKey(expectedStringDayBeforeYesterday), is(true));
    assertThat(resultMap.get(expectedStringDayBeforeYesterday), is(2000L));
  }

  private Object[] parametersForEmptyIntervalBetweenTwoProcessInstances() {
    return new Object[]{
      new Object[]{AVG_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 4000L},
      new Object[]{MIN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 1000L},
      new Object[]{MAX_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 9000L},
      new Object[]{MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART, 2000L}
    };
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void groupedByHour(ReportDataType reportDataType) throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(5);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.HOURS);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setDateInterval(GroupByDateUnit.HOUR)
            .setProcessDefinitionKey(dto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    assertDateResultMap(resultMap, 5, now, ChronoUnit.HOURS);
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void groupedByDay(ReportDataType reportDataType) throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.DAYS);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setDateInterval(GroupByDateUnit.DAY)
            .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.DAYS);
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void groupedByWeek(ReportDataType reportDataType) throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.WEEKS);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setDateInterval(GroupByDateUnit.WEEK)
            .setProcessDefinitionKey(dto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.WEEKS);
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void groupedByMonth(ReportDataType reportDataType) throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.MONTHS);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setDateInterval(GroupByDateUnit.MONTH)
            .setProcessDefinitionKey(dto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.MONTHS);
  }

  @Test
  @Parameters(source = ReportDataTypeProvider.class)
  public void groupedByYear(ReportDataType reportDataType) throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.YEARS);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setDateInterval(GroupByDateUnit.YEAR)
            .setProcessDefinitionKey(dto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> resultMap = result.getResult();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.YEARS);
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

  private void assertDateResultMap(Map<String, Long> resultMap, int size, OffsetDateTime now, ChronoUnit unit) {
    assertThat(resultMap.size(), is(size));
    final OffsetDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit);
    IntStream.range(0, size)
      .forEach(i -> {
        String expectedDateString = localDateTimeToString(finalStartOfUnit.minus(i, unit));
        assertThat(resultMap.containsKey(expectedDateString), is(true));
        assertThat(resultMap.get(expectedDateString), is(1000L));
      });
  }

  private OffsetDateTime truncateToStartOfUnit(OffsetDateTime date, ChronoUnit unit) {
    OffsetDateTime truncatedDate;
    if (unit.equals(ChronoUnit.HOURS) || unit.equals(ChronoUnit.DAYS)) {
      truncatedDate = date.truncatedTo(unit);
    } else if (unit.equals(ChronoUnit.WEEKS)) {
      truncatedDate = date.with(DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS);
    } else if (unit.equals(ChronoUnit.MONTHS)) {
      truncatedDate = date.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    } else {
      // it should be year
      truncatedDate = date.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
    }
    return truncatedDate;
  }

  private void updateProcessInstancesDates(List<ProcessInstanceEngineDto> procInsts,
                                           OffsetDateTime now,
                                           ChronoUnit unit) throws SQLException {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    Map<String, OffsetDateTime> idToNewEndDate = new HashMap<>();
    for (int i = 0; i < procInsts.size(); i++) {
      String id = procInsts.get(i).getId();
      OffsetDateTime newStartDate = now.minus(i, unit);
      idToNewStartDate.put(id, newStartDate);
      idToNewEndDate.put(id, newStartDate.plusSeconds(1L));
      engineDatabaseRule.changeActivityInstanceStartDate(id, now);
      engineDatabaseRule.changeActivityInstanceEndDate(id, now.plusSeconds(1));
    }
    engineDatabaseRule.updateProcessInstanceStartDates(idToNewStartDate);
    engineDatabaseRule.updateProcessInstanceEndDates(idToNewEndDate);

  }

  private List<ProcessFilterDto> createVariableFilter(String value) {
    VariableFilterDto variableFilterDto = createBooleanVariableFilter("var", value);
    return Collections.singletonList(variableFilterDto);
  }


  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    ProcessDefinitionEngineDto processDefinitionEngineDto = deploySimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceEngineDto =
      engineRule.startProcessInstance(processDefinitionEngineDto.getId());
    processInstanceEngineDto.setProcessDefinitionKey(processDefinitionEngineDto.getKey());
    processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinitionEngineDto.getVersion()));
    return processInstanceEngineDto;
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(processModel);
  }

  private ProcessInstanceEngineDto deployAndStartLoopingProcess() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
    .startEvent("startEvent")
    .exclusiveGateway(START_LOOP)
      .serviceTask()
        .camundaExpression("${true}")
      .exclusiveGateway(END_LOOP)
        .condition("Take another round", "${!anotherRound}")
      .endEvent("endEvent")
    .moveToLastGateway()
      .condition("End process", "${anotherRound}")
      .serviceTask("serviceTask")
        .camundaExpression("${true}")
        .camundaInputParameter("anotherRound", "${anotherRound}")
        .camundaOutputParameter("anotherRound", "${!anotherRound}")
      .scriptTask("scriptTask")
        .scriptFormat("groovy")
        .scriptText("sleep(10)")
      .connectTo("mergeExclusiveGateway")
    .done();
    Map<String, Object> variables = new HashMap<>();
    variables.put("anotherRound", true);
    return engineRule.deployAndStartProcessWithVariables(modelInstance, variables);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask(TEST_ACTIVITY)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private MapProcessReportResultDto evaluateReport(ProcessReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(MapProcessReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSingleUnsavedReportRequest(reportData)
            .execute();
  }

  private String createAndStoreDefaultReportDefinition(ProcessReportDataDto reportData) {
    String id = createNewReport();

    SingleReportDefinitionDto<ProcessReportDataDto> report = new SingleReportDefinitionDto<>();
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
            .buildCreateSingleReportRequest()
            .execute(IdDto.class, 200)
            .getId();
  }

  private MapProcessReportResultDto evaluateReportById(String reportId) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSavedReportRequest(reportId)
            .execute(MapProcessReportResultDto.class, 200);
  }

  public static class ReportDataTypeProvider {
    public static Object[] provideReportDataType() {
      return new Object[]{
        new Object[]{AVG_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART},
        new Object[]{MIN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART},
        new Object[]{MAX_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART},
        new Object[]{MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART}
      };
    }
  }

  private void adjustProcessInstanceDates(String processInstanceId,
                                          OffsetDateTime startDate,
                                          long daysToShift) throws SQLException {
    OffsetDateTime shiftedStartDate = startDate.plusDays(daysToShift);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceId, shiftedStartDate);
  }

  private String localDateTimeToString(OffsetDateTime time) {
    return embeddedOptimizeRule.getDateTimeFormatter().format(time.withOffsetSameInstant(ZoneOffset.UTC));
  }

  private void startThreeProcessInstances(OffsetDateTime procInstStartDate,
                                          int daysToShiftProcessInstance,
                                          ProcessDefinitionEngineDto procDefDto,
                                          List<Integer> activityDurationsInSec) throws
                                                                           SQLException {
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, daysToShiftProcessInstance);

    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto2.getId(), procInstStartDate, daysToShiftProcessInstance);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstStartDate, daysToShiftProcessInstance);

    Map<String, OffsetDateTime> activityStartDatesToUpdate = new HashMap<>();
    Map<String, OffsetDateTime> endDatesToUpdate = new HashMap<>();
    activityStartDatesToUpdate.put(processInstanceDto.getId(), activityStartDate);
    activityStartDatesToUpdate.put(processInstanceDto2.getId(), activityStartDate);
    activityStartDatesToUpdate.put(processInstanceDto3.getId(), activityStartDate);
    endDatesToUpdate.put(processInstanceDto.getId(), activityStartDate.plusSeconds(activityDurationsInSec.get(0)));
    endDatesToUpdate.put(processInstanceDto2.getId(), activityStartDate.plusSeconds(activityDurationsInSec.get(1)));
    endDatesToUpdate.put(processInstanceDto3.getId(), activityStartDate.plusSeconds(activityDurationsInSec.get(2)));

    engineDatabaseRule.updateActivityInstanceStartDates(activityStartDatesToUpdate);
    engineDatabaseRule.updateActivityInstanceEndDates(endDatesToUpdate);
  }

}
