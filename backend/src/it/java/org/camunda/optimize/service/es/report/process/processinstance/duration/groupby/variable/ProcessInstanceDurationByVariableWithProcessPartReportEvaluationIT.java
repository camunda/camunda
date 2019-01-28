package org.camunda.optimize.service.es.report.process.processinstance.duration.groupby.variable;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewOperation;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.VariableType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
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
  private static final VariableType DEFAULT_VARIABLE_TYPE = VariableType.STRING;
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
  public void reportEvaluationForOneProcess(ProcessReportDataType reportDataType, ProcessViewOperation operation) throws Exception {

    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      startDate
    );
    engineDatabaseRule.changeActivityInstanceEndDateForProcessDefinition(processInstanceDto.getDefinitionId(), endDate);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setVariableName(DEFAULT_VARIABLE_NAME)
            .setVariableType(DEFAULT_VARIABLE_TYPE)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .build();

    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.VARIABLE));
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
      new Object[]{ProcessReportDataType.AVG_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, ProcessViewOperation.AVG},
      new Object[]{ProcessReportDataType.MIN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, ProcessViewOperation.MIN},
      new Object[]{ProcessReportDataType.MAX_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, ProcessViewOperation.MAX},
      new Object[]{ProcessReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, ProcessViewOperation.MEDIAN}
    };
  }

  @Test
  @Parameters
  public void reportEvaluationById(ProcessReportDataType reportDataType, ProcessViewOperation operation) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      startDate
    );
    engineDatabaseRule.changeActivityInstanceEndDateForProcessDefinition(processInstanceDto.getDefinitionId(), endDate);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    ProcessReportDataDto reportData = ProcessReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setVariableName(DEFAULT_VARIABLE_NAME)
            .setVariableType(DEFAULT_VARIABLE_TYPE)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .build();

    String reportId = createAndStoreDefaultReportDefinition(reportData);

    // when
    ProcessReportMapResultDto result = evaluateReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));

    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.VARIABLE));
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
      new Object[]{ProcessReportDataType.AVG_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, ProcessViewOperation.AVG},
      new Object[]{ProcessReportDataType.MIN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, ProcessViewOperation.MIN},
      new Object[]{ProcessReportDataType.MAX_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, ProcessViewOperation.MAX},
      new Object[]{ProcessReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, ProcessViewOperation.MEDIAN}
    };
  }

  @Test
  @Parameters
  public void evaluateReportForMultipleEvents(ProcessReportDataType reportDataType,
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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processEngineDto.getKey())
            .setProcessDefinitionVersion(processEngineDto.getVersionAsString())
            .setVariableName(DEFAULT_VARIABLE_NAME)
            .setVariableType(DEFAULT_VARIABLE_TYPE)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .build();
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(2));
    assertThat(variableValueToCount.get(DEFAULT_VARIABLE_VALUE), is(firstVariableDuration));
    assertThat(variableValueToCount.get(DEFAULT_VARIABLE_VALUE+2), is(secondVariableDuration));
  }

  private Object[] parametersForEvaluateReportForMultipleEvents() {
    return new Object[]{
      new Object[]{ProcessReportDataType.AVG_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, 4000L, 1000L},
      new Object[]{ProcessReportDataType.MIN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, 1000L, 1000L},
      new Object[]{ProcessReportDataType.MAX_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, 9000L, 1000L},
      new Object[]{ProcessReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, 2000L, 1000L}
    };
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void takeCorrectActivityOccurrences(ProcessReportDataType reportDataType) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartLoopingProcess();
    engineDatabaseRule.changeFirstActivityInstanceStartDate(START_LOOP, startDate);
    engineDatabaseRule.changeFirstActivityInstanceEndDate(END_LOOP, startDate.plusSeconds(2));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setVariableName(DEFAULT_VARIABLE_NAME)
            .setVariableType(DEFAULT_VARIABLE_TYPE)
            .setStartFlowNodeId(START_LOOP)
            .setEndFlowNodeId(END_LOOP)
            .build();

    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().get(DEFAULT_VARIABLE_VALUE), is(2000L));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void unknownStartReturnsZero(ProcessReportDataType reportDataType) throws SQLException {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceEndDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().plusHours(1)
    );
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setVariableName(DEFAULT_VARIABLE_NAME)
            .setVariableType(DEFAULT_VARIABLE_TYPE)
            .setStartFlowNodeId("foo")
            .setEndFlowNodeId(END_EVENT)
            .build();

    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().isEmpty(), is(true));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void unknownEndReturnsZero(ProcessReportDataType reportDataType) throws SQLException {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().minusHours(1)
    );
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setVariableName(DEFAULT_VARIABLE_NAME)
            .setVariableType(DEFAULT_VARIABLE_TYPE)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId("FooFOO")
            .build();
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().isEmpty(), is(true));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void noAvailableProcessInstancesReturnsZero(ProcessReportDataType reportDataType) {
    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey("FOOPROC")
            .setProcessDefinitionVersion("1")
            .setVariableName(DEFAULT_VARIABLE_NAME)
            .setVariableType(DEFAULT_VARIABLE_TYPE)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .build();

    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().isEmpty(), is(true));
  }

  @Test
  @Parameters
  public void reportAcrossAllVersions(ProcessReportDataType reportDataType, Long expectedDuration) throws Exception {
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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(ALL_VERSIONS)
            .setVariableName(DEFAULT_VARIABLE_NAME)
            .setVariableType(DEFAULT_VARIABLE_TYPE)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .build();
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(ALL_VERSIONS));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get(DEFAULT_VARIABLE_VALUE), is(expectedDuration));
  }

  private Object[] parametersForReportAcrossAllVersions() {
    return new Object[]{
      new Object[]{ProcessReportDataType.AVG_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, 4000L},
      new Object[]{ProcessReportDataType.MIN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, 1000L},
      new Object[]{ProcessReportDataType.MAX_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, 9000L},
      new Object[]{ProcessReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, 2000L}
    };
  }

  @Test
  @Parameters
  public void otherProcessDefinitionsDoNoAffectResult(ProcessReportDataType reportDataType, Long expectedDuration) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(startDate, procDefDto, Arrays.asList(1, 2, 9));

    ProcessInstanceEngineDto procInstDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceStartDate(procInstDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(procInstDto.getId(), startDate.plusSeconds(2));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(procDefDto.getKey())
            .setProcessDefinitionVersion(procDefDto.getVersionAsString())
            .setVariableName(DEFAULT_VARIABLE_NAME)
            .setVariableType(DEFAULT_VARIABLE_TYPE)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .build();

    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get(DEFAULT_VARIABLE_VALUE), is(expectedDuration));
  }

  private Object[] parametersForOtherProcessDefinitionsDoNoAffectResult() {
    return new Object[]{
      new Object[]{ProcessReportDataType.AVG_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, 4000L},
      new Object[]{ProcessReportDataType.MIN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, 1000L},
      new Object[]{ProcessReportDataType.MAX_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, 9000L},
      new Object[]{ProcessReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART, 2000L}
    };
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void filterInReportWorks(ProcessReportDataType reportDataType) throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE);
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto =
      deployAndStartSimpleUserTaskProcessWithVariables(variables);
    engineRule.finishAllUserTasks(processInstanceDto.getId());
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setVariableName(DEFAULT_VARIABLE_NAME)
            .setVariableType(DEFAULT_VARIABLE_TYPE)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(TEST_ACTIVITY)
            .build();

    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get(DEFAULT_VARIABLE_VALUE), is(1000L));

    // when
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(4));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    reportData.setFilter(Collections.singletonList(new RunningInstancesOnlyFilterDto()));
    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get(DEFAULT_VARIABLE_VALUE), is(4000L));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void variableTypeIsImportant(ProcessReportDataType reportDataType) throws SQLException {
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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setVariableName(DEFAULT_VARIABLE_NAME)
            .setVariableType(DEFAULT_VARIABLE_TYPE)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .build();

    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get("1"), is(1000L));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void otherVariablesDoNotDistortTheResult(ProcessReportDataType reportDataType) throws SQLException {
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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setVariableName("foo1")
            .setVariableType(DEFAULT_VARIABLE_TYPE)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .build();
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get("bar1"), is(1000L));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void worksWithAllVariableTypes(ProcessReportDataType reportDataType) throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, VariableType> varNameToTypeMap = createVarNameToTypeMap();
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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      VariableType variableType = varNameToTypeMap.get(entry.getKey());
      ProcessReportDataDto reportData = ProcessReportDataBuilder
              .createReportData()
              .setReportDataType(reportDataType)
              .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
              .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
              .setVariableName(entry.getKey())
              .setVariableType(variableType)
              .setStartFlowNodeId(START_EVENT)
              .setEndFlowNodeId(END_EVENT)
              .build();
      ProcessReportMapResultDto result = evaluateReport(reportData);

      // then
      assertThat(result.getResult(), is(notNullValue()));
      Map<String, Long> variableValueToCount = result.getResult();
      assertThat(variableValueToCount.size(), is(1));
      if (VariableType.DATE.equals(variableType)) {
        OffsetDateTime temporal = (OffsetDateTime) variables.get(entry.getKey());
        String dateAsString =
          embeddedOptimizeRule.getDateTimeFormatter().format(temporal.withOffsetSameLocal(ZoneOffset.UTC));
        assertThat(variableValueToCount.get(dateAsString), is(1000L));
      } else {
        assertThat(variableValueToCount.get(entry.getValue().toString()), is(1000L));
      }
    }
  }

  private Map<String, VariableType> createVarNameToTypeMap() {
    Map<String, VariableType> varToType = new HashMap<>();
    varToType.put("dateVar", VariableType.DATE);
    varToType.put("boolVar", VariableType.BOOLEAN);
    varToType.put("shortVar", VariableType.SHORT);
    varToType.put("intVar", VariableType.INTEGER);
    varToType.put("longVar", VariableType.LONG);
    varToType.put("doubleVar", VariableType.DOUBLE);
    varToType.put("stringVar", VariableType.STRING);
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

  private ProcessReportMapResultDto evaluateReport(ProcessReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(ProcessReportMapResultDto.class);
  }

  private String createAndStoreDefaultReportDefinition(ProcessReportDataDto reportData) {
    String id = createNewProcessReport();

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

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
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

  private String createNewProcessReport() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateSingleProcessReportRequest()
            .execute(IdDto.class, 200)
            .getId();
  }

  private ProcessReportMapResultDto evaluateReportById(String reportId) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSavedReportRequest(reportId)
            .execute(ProcessReportMapResultDto.class, 200);
  }

  public static class ReportDataBuilderProvider {
    public static Object[] provideReportDataCreator() {
      return new Object[]{
        new Object[]{ProcessReportDataType.AVG_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART},
        new Object[]{ProcessReportDataType.MIN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART},
        new Object[]{ProcessReportDataType.MAX_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART},
        new Object[]{ProcessReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART}
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
