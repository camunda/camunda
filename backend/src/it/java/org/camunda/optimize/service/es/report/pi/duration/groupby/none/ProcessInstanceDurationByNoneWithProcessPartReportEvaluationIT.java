package org.camunda.optimize.service.es.report.pi.duration.groupby.none;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberSingleReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataBuilder;
import org.camunda.optimize.test.util.ReportDataType;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryRequestBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_NONE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_AVERAGE_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_DURATION_PROPERTY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MAX_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MEDIAN_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MIN_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_PROCESS_INSTANCE_ENTITY;
import static org.camunda.optimize.test.util.VariableFilterUtilHelper.createBooleanVariableFilter;
import static org.elasticsearch.script.Script.DEFAULT_SCRIPT_LANG;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public class ProcessInstanceDurationByNoneWithProcessPartReportEvaluationIT {

  private static final String END_EVENT = "endEvent";
  private static final String START_EVENT = "startEvent";
  private static final String START_LOOP = "mergeExclusiveGateway";
  private static final String END_LOOP = "splittingGateway";
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
  public void reportEvaluationForOneProcess(ReportDataType reportDataType, String operation) throws Exception {

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
    SingleReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_PROCESS_INSTANCE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_DURATION_PROPERTY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(GROUP_BY_NONE_TYPE));
    assertThat(resultReportDataDto.getParameters().getProcessPart(), is(notNullValue()));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(1000L));
  }

  private Object[] parametersForReportEvaluationForOneProcess() {
    return new Object[]{
      new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, VIEW_AVERAGE_OPERATION},
      new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, VIEW_MIN_OPERATION},
      new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, VIEW_MAX_OPERATION},
      new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, VIEW_MEDIAN_OPERATION}
    };
  }

  @Test
  @Parameters
  public void reportEvaluationById(ReportDataType reportDataType, String operation) throws Exception {
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
    SingleReportDataDto reportDataDto = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    String reportId = createAndStoreDefaultReportDefinition(reportDataDto);

    // when
    NumberSingleReportResultDto result = evaluateReportById(reportId);

    // then
    SingleReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));

    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_PROCESS_INSTANCE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_DURATION_PROPERTY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(GROUP_BY_NONE_TYPE));
    assertThat(resultReportDataDto.getParameters().getProcessPart(), is(notNullValue()));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(1000L));
  }

  private Object[] parametersForReportEvaluationById() {
    return new Object[]{
      new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, VIEW_AVERAGE_OPERATION},
      new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, VIEW_MIN_OPERATION},
      new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, VIEW_MAX_OPERATION},
      new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, VIEW_MEDIAN_OPERATION}
    };
  }

  @Test
  @Parameters
  public void evaluateReportForMultipleEvents(ReportDataType reportDataType, Long expectedDuration) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    Map<String, OffsetDateTime> startDatesToUpdate = new HashMap<>();
    startDatesToUpdate.put(processInstanceDto.getId(), startDate);
    startDatesToUpdate.put(processInstanceDto2.getId(), startDate);
    startDatesToUpdate.put(processInstanceDto3.getId(), startDate);
    engineDatabaseRule.updateActivityInstanceStartDates(startDatesToUpdate);
    Map<String, OffsetDateTime> endDatesToUpdate = new HashMap<>();
    endDatesToUpdate.put(processInstanceDto.getId(), startDate.plusSeconds(1));
    endDatesToUpdate.put(processInstanceDto2.getId(), startDate.plusSeconds(2));
    endDatesToUpdate.put(processInstanceDto3.getId(), startDate.plusSeconds(9));
    engineDatabaseRule.updateActivityInstanceEndDates(endDatesToUpdate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();
    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(expectedDuration));
  }

  private Object[] parametersForEvaluateReportForMultipleEvents() {
    return new Object[]{
      new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, 4000L},
      new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, 1000L},
      new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, 9000L},
      new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, 2000L}
    };
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void takeCorrectActivityOccurrences(ReportDataType reportDataType) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartLoopingProcess();
    engineDatabaseRule.changeFirstActivityInstanceStartDate(START_LOOP, startDate);
    engineDatabaseRule.changeFirstActivityInstanceEndDate(END_LOOP, startDate.plusSeconds(2));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_LOOP)
            .setEndFlowNodeId(END_LOOP)
            .setReportDataType(reportDataType)
            .build();

    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(2000L));
  }

  /**
   * When migrating from Optimize 2.1 to 2.2 then all the activity instances
   * that were imported in 2.1 don't have a start and end date. This test
   * ensures that Optimize can cope with that.
   */
  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void activityHasNullDates(ReportDataType reportDataType) {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    setActivityStartDatesToNull();

    // when
    SingleReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(0L));
  }

  private void setActivityStartDatesToNull() {
    UpdateByQueryRequestBuilder updateByQuery =
      UpdateByQueryAction.INSTANCE.newRequestBuilder(elasticSearchRule.getClient());
    ConfigurationService configurationService = embeddedOptimizeRule.getConfigurationService();
    String processInstanceIndex = configurationService.getOptimizeIndex(configurationService.getProcessInstanceType());
    Script setActivityStartDatesToNull = new Script(
      ScriptType.INLINE,
      DEFAULT_SCRIPT_LANG,
      "for (event in ctx._source.events) { event.startDate= null }",
      Collections.emptyMap()
    );
    updateByQuery.source(processInstanceIndex)
    .abortOnVersionConflict(false)
    .script(setActivityStartDatesToNull);
    updateByQuery.refresh(true).get();
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void firstOccurrenceOfEndDateIsBeforeFirstOccurrenceOfStartDate(ReportDataType reportDataType) throws
                                                                                                              Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeFirstActivityInstanceStartDate(START_EVENT, startDate);
    engineDatabaseRule.changeFirstActivityInstanceEndDate(END_EVENT, startDate.minusSeconds(2));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(0L));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void unknownStartReturnsZero(ReportDataType reportDataType) throws SQLException {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceEndDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().plusHours(1)
    );
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId("FOoO")
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(0L));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void unknownEndReturnsZero(ReportDataType reportDataType) throws SQLException {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().minusHours(1)
    );
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId("FOO")
            .setReportDataType(reportDataType)
            .build();

    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(0L));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void noAvailableProcessInstancesReturnsZero(ReportDataType reportDataType) {
    // when
    SingleReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey("FOOPROCDEF")
            .setProcessDefinitionVersion("1")
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(0L));
  }

  @Test
  @Parameters
  public void reportAcrossAllVersions(ReportDataType reportDataType, Long expectedDuration) throws Exception {
    //given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(9));
    processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(2));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(ALL_VERSIONS)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(expectedDuration));
  }

  private Object[] parametersForReportAcrossAllVersions() {
    return new Object[]{
      new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, 4000L},
      new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, 1000L},
      new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, 9000L},
      new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, 2000L}
    };
  }

  @Test
  @Parameters
  public void otherProcessDefinitionsDoNoAffectResult(ReportDataType reportDataType, Long expectedDuration) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(9));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(2));
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processDefinitionKey)
            .setProcessDefinitionVersion(processDefinitionVersion)
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(expectedDuration));
  }

  private Object[] parametersForOtherProcessDefinitionsDoNoAffectResult() {
    return new Object[]{
      new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, 4000L},
      new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, 1000L},
      new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, 9000L},
      new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART, 2000L}
    };
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void filterInReportWorks(ReportDataType reportDataType) throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setStartFlowNodeId(START_EVENT)
            .setEndFlowNodeId(END_EVENT)
            .setReportDataType(reportDataType)
            .build();

    reportData.setFilter(createVariableFilter("true"));
    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(1000L));

    // when
    reportData.setFilter(createVariableFilter("false"));
    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(0L));
  }

  private List<FilterDto> createVariableFilter(String value) {
    VariableFilterDto variableFilterDto = createBooleanVariableFilter("var", value);
    return Collections.singletonList(variableFilterDto);
  }


  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployAndStartProcess(processModel);
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

  private NumberSingleReportResultDto evaluateReport(SingleReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(NumberSingleReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(SingleReportDataDto reportData) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSingleUnsavedReportRequest(reportData)
            .execute();
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

  private NumberSingleReportResultDto evaluateReportById(String reportId) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSavedReportRequest(reportId)
            .execute(NumberSingleReportResultDto.class, 200);
  }

  public static class ReportDataBuilderProvider {
    public static Object[] provideReportDataCreator() {
      return new Object[]{
        new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART},
        new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART},
        new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART},
        new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART}
      };
    }
  }

}
