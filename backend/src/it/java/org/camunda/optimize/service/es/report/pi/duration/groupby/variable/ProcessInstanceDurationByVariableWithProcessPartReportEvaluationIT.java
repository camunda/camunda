package org.camunda.optimize.service.es.report.pi.duration.groupby.variable;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.RunningInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapSingleReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.creator.ReportDataCreator;
import org.camunda.optimize.service.es.report.util.creator.avg.AvgProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator;
import org.camunda.optimize.service.es.report.util.creator.max.MaxProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator;
import org.camunda.optimize.service.es.report.util.creator.median.MedianProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator;
import org.camunda.optimize.service.es.report.util.creator.min.MinProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator;
import org.camunda.optimize.service.util.VariableHelper;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_VARIABLE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_AVERAGE_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_DURATION_PROPERTY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MAX_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MEDIAN_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MIN_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_PROCESS_INSTANCE_ENTITY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public class ProcessInstanceDurationByVariableWithProcessPartReportEvaluationIT {

  private static final String PROCESS_DEFINITION_KEY = "123";
  private static final String END_EVENT = "endEvent";
  private static final String START_EVENT = "startEvent";
  private static final String START_LOOP = "mergeExclusiveGateway";
  private static final String END_LOOP = "splittingGateway";
  private static final String DEFAULT_VARIABLE_NAME = "foo";
  private static final String DEFAULT_VARIABLE_VALUE = "bar";
  private static final String DEFAULT_VARIABLE_TYPE = "String";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  private static final String TEST_ACTIVITY = "testActivity";

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule)
      .around(engineDatabaseRule);

  @Test
  @Parameters
  public void reportEvaluationForOneProcess(ReportDataCreator creator, String operation) throws Exception {

    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      startDate
    );
    engineDatabaseRule.changeActivityInstanceEndDateForProcessDefinition(processInstanceDto.getDefinitionId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = creator.create(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      DEFAULT_VARIABLE_NAME,
      DEFAULT_VARIABLE_TYPE,
      START_EVENT,
      END_EVENT
    );
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_PROCESS_INSTANCE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_DURATION_PROPERTY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(GROUP_BY_VARIABLE_TYPE));
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName(), is(DEFAULT_VARIABLE_NAME));
    assertThat(variableGroupByDto.getValue().getType(), is(DEFAULT_VARIABLE_TYPE));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.get(DEFAULT_VARIABLE_VALUE), is(1000L));
  }

  private Object[] parametersForReportEvaluationForOneProcess() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), VIEW_AVERAGE_OPERATION},
      new Object[]{new MinProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), VIEW_MIN_OPERATION},
      new Object[]{new MaxProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), VIEW_MAX_OPERATION},
      new Object[]{new MedianProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), VIEW_MEDIAN_OPERATION}
    };
  }

  @Test
  @Parameters
  public void reportEvaluationById(ReportDataCreator creator, String operation) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      startDate
    );
    engineDatabaseRule.changeActivityInstanceEndDateForProcessDefinition(processInstanceDto.getDefinitionId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    SingleReportDataDto reportData = creator.create(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      DEFAULT_VARIABLE_NAME,
      DEFAULT_VARIABLE_TYPE,
      START_EVENT,
      END_EVENT
    );
    String reportId = createAndStoreDefaultReportDefinition(reportData);

    // when
    MapSingleReportResultDto result = evaluateReportById(reportId);

    // then
    SingleReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));

    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_PROCESS_INSTANCE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_DURATION_PROPERTY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(GROUP_BY_VARIABLE_TYPE));
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName(), is(DEFAULT_VARIABLE_NAME));
    assertThat(variableGroupByDto.getValue().getType(), is(DEFAULT_VARIABLE_TYPE));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.get(DEFAULT_VARIABLE_VALUE), is(1000L));
  }

  private Object[] parametersForReportEvaluationById() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), VIEW_AVERAGE_OPERATION},
      new Object[]{new MinProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), VIEW_MIN_OPERATION},
      new Object[]{new MaxProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), VIEW_MAX_OPERATION},
      new Object[]{new MedianProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), VIEW_MEDIAN_OPERATION}
    };
  }

  @Test
  @Parameters
  public void evaluateReportForMultipleEvents(ReportDataCreator reportDataCreator,
                                              long firstVariableDuration,
                                              long secondVariableDuration) throws Exception {
    // given

    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processEngineDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(startDate, processEngineDto, Arrays.asList(1, 2, 9));
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE + 2);
    ProcessInstanceEngineDto processInstanceDto =
      engineRule.startProcessInstance(processEngineDto.getId(), variables);
    engineDatabaseRule.changeActivityInstanceStartDate(
      processInstanceDto.getId(),
      startDate
    );
    engineDatabaseRule.changeActivityInstanceEndDate(
      processInstanceDto.getId(),
      startDate.plusSeconds(1)
    );
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData =
      reportDataCreator.create(
        processEngineDto.getKey(),
        processEngineDto.getVersionAsString(),
        DEFAULT_VARIABLE_NAME,
        DEFAULT_VARIABLE_TYPE,
        START_EVENT,
        END_EVENT);
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(2));
    assertThat(variableValueToCount.get(DEFAULT_VARIABLE_VALUE), is(firstVariableDuration));
    assertThat(variableValueToCount.get(DEFAULT_VARIABLE_VALUE+2), is(secondVariableDuration));
  }

  private Object[] parametersForEvaluateReportForMultipleEvents() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), 4000L, 1000L},
      new Object[]{new MinProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), 1000L, 1000L},
      new Object[]{new MaxProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), 9000L, 1000L},
      new Object[]{new MedianProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), 2000L, 1000L}
    };
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void takeCorrectActivityOccurrences(ReportDataCreator reportDataCreator) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartLoopingProcess();
    engineDatabaseRule.changeFirstActivityInstanceStartDate(START_LOOP, startDate);
    engineDatabaseRule.changeFirstActivityInstanceEndDate(END_LOOP, startDate.plusSeconds(2));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData =
      reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        DEFAULT_VARIABLE_NAME,
        DEFAULT_VARIABLE_TYPE,
        START_LOOP,
        END_LOOP);
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().get(DEFAULT_VARIABLE_VALUE), is(2000L));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void unknownStartReturnsZero(ReportDataCreator reportDataCreator) throws SQLException {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceEndDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().plusHours(1)
    );
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData =
      reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        DEFAULT_VARIABLE_NAME,
        DEFAULT_VARIABLE_TYPE,
        "foo",
        END_EVENT);
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().isEmpty(), is(true));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void unknownEndReturnsZero(ReportDataCreator reportDataCreator) throws SQLException {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().minusHours(1)
    );
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData =
      reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        DEFAULT_VARIABLE_NAME,
        DEFAULT_VARIABLE_TYPE,
        START_EVENT,
        "FOo");
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().isEmpty(), is(true));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void noAvailableProcessInstancesReturnsZero(ReportDataCreator reportDataCreator) {
    // when
    SingleReportDataDto reportData =
      reportDataCreator.create(
        "fooProcessDefinition",
        "1",
        DEFAULT_VARIABLE_NAME,
        DEFAULT_VARIABLE_TYPE,
        START_EVENT,
        END_EVENT);
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().isEmpty(), is(true));
  }

  @Test
  @Parameters
  public void reportAcrossAllVersions(ReportDataCreator reportDataCreator, Long expectedDuration) throws Exception {
    //given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(9));
    processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(2));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = reportDataCreator.create(
      processInstanceDto.getProcessDefinitionKey(),
      ALL_VERSIONS,
      DEFAULT_VARIABLE_NAME,
      DEFAULT_VARIABLE_TYPE,
      START_EVENT,
      END_EVENT
    );
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(ALL_VERSIONS));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get(DEFAULT_VARIABLE_VALUE), is(expectedDuration));
  }

  private Object[] parametersForReportAcrossAllVersions() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), 4000L},
      new Object[]{new MinProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), 1000L},
      new Object[]{new MaxProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), 9000L},
      new Object[]{new MedianProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), 2000L}
    };
  }

  @Test
  @Parameters
  public void otherProcessDefinitionsDoNoAffectResult(ReportDataCreator reportDataCreator, Long expectedDuration) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(startDate, procDefDto, Arrays.asList(1, 2, 9));

    ProcessInstanceEngineDto procInstDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceStartDate(procInstDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(procInstDto.getId(), startDate.plusSeconds(2));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = reportDataCreator.create(
        procDefDto.getKey(),
        procDefDto.getVersionAsString(),
        DEFAULT_VARIABLE_NAME,
        DEFAULT_VARIABLE_TYPE,
        START_EVENT,
        END_EVENT
    );
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get(DEFAULT_VARIABLE_VALUE), is(expectedDuration));
  }

  private Object[] parametersForOtherProcessDefinitionsDoNoAffectResult() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), 4000L},
      new Object[]{new MinProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), 1000L},
      new Object[]{new MaxProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), 9000L},
      new Object[]{new MedianProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator(), 2000L}
    };
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void filterInReportWorks(ReportDataCreator reportDataCreator) throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE);
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto =
      deployAndStartSimpleUserTaskProcessWithVariables(variables);
    engineRule.finishAllUserTasks(processInstanceDto.getId());
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        DEFAULT_VARIABLE_NAME,
        DEFAULT_VARIABLE_TYPE,
        START_EVENT,
        TEST_ACTIVITY
    );
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get(DEFAULT_VARIABLE_VALUE), is(1000L));

    // when
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(4));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    reportData.setFilter(Collections.singletonList(new RunningInstancesOnlyFilterDto()));
    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get(DEFAULT_VARIABLE_VALUE), is(4000L));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void variableTypeIsImportant(ReportDataCreator reportDataCreator) throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, "1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    variables.put(DEFAULT_VARIABLE_NAME, 1);
    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto2.getId(), startDate.plusSeconds(2));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
      DEFAULT_VARIABLE_NAME,
        DEFAULT_VARIABLE_TYPE,
        START_EVENT,
        END_EVENT
    );
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get("1"), is(1000L));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void otherVariablesDoNotDistortTheResult(ReportDataCreator reportDataCreator) throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo1", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    variables.clear();
    variables.put("foo2", "bar1");
    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto2.getId(), startDate.plusSeconds(5));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
      "foo1",
        "String",
        START_EVENT,
        END_EVENT
    );
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get("bar1"), is(1000L));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void worksWithAllVariableTypes(ReportDataCreator reportDataCreator) throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, String> varNameToTypeMap = createVarNameToTypeMap();
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now().withOffsetSameLocal(ZoneOffset.UTC));
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      String variableType = varNameToTypeMap.get(entry.getKey());
      SingleReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        entry.getKey(),
        variableType,
        START_EVENT,
        END_EVENT
      );
      MapSingleReportResultDto result = evaluateReport(reportData);

      // then
      assertThat(result.getResult(), is(notNullValue()));
      Map<String, Long> variableValueToCount = result.getResult();
      assertThat(variableValueToCount.size(), is(1));
      if (VariableHelper.isDateType(variableType)) {
        OffsetDateTime temporal = (OffsetDateTime) variables.get(entry.getKey());
        String dateAsString =
          embeddedOptimizeRule.getDateTimeFormatter().format(temporal.withOffsetSameLocal(ZoneOffset.UTC));
        assertThat(variableValueToCount.get(dateAsString), is(1000L));
      } else {
        assertThat(variableValueToCount.get(entry.getValue().toString()), is(1000L));
      }
    }
  }

  private Map<String, String> createVarNameToTypeMap() {
    Map<String, String> varToType = new HashMap<>();
    varToType.put("dateVar", "date");
    varToType.put("boolVar", "boolean");
    varToType.put("shortVar", "short");
    varToType.put("intVar", "integer");
    varToType.put("longVar", "long");
    varToType.put("doubleVar", "double");
    varToType.put("stringVar", "string");
    return varToType;
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE);
    return deployAndStartSimpleServiceTaskProcess(variables);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
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
    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE);
    return engineRule.deployAndStartProcessWithVariables(modelInstance, variables);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask(TEST_ACTIVITY)
        .camundaExpression("${true}")
      .userTask("userTask")
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
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

  private MapSingleReportResultDto evaluateReport(SingleReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(MapSingleReportResultDto.class);
  }

  private String createAndStoreDefaultReportDefinition(SingleReportDataDto reportData) {
    String id = createNewReport();

    SingleReportDefinitionDto report = new SingleReportDefinitionDto();
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

  private Response evaluateReportAndReturnResponse(SingleReportDataDto reportData) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSingleUnsavedReportRequest(reportData)
            .execute();
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

  private MapSingleReportResultDto evaluateReportById(String reportId) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSavedReportRequest(reportId)
            .execute(MapSingleReportResultDto.class, 200);
  }

  public static class ReportDataCreatorProvider {
    public static Object[] provideReportDataCreator() {
      return new Object[]{
        new Object[]{new AvgProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator()},
        new Object[]{new MinProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator()},
        new Object[]{new MaxProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator()},
        new Object[]{new MedianProcessInstanceDurationGroupByVariableWithProcessPartReportDataCreator()}
      };
    }
  }

  private void startThreeProcessInstances(OffsetDateTime activityStartDate,
                                          ProcessDefinitionEngineDto procDefDto,
                                          List<Integer> activityDurationsInSec) throws
                                                                           SQLException {
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE);
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(procDefDto.getId(), variables);
    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(procDefDto.getId(), variables);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(procDefDto.getId(), variables);

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
