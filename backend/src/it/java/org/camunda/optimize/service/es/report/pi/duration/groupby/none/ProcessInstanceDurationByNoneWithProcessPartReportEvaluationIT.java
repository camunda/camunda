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
import org.camunda.optimize.service.es.report.command.util.ReportConstants;
import org.camunda.optimize.service.es.report.util.creator.ReportDataCreator;
import org.camunda.optimize.service.es.report.util.creator.avg.AvgProcessInstanceDurationByNoneWithProcessPartReportDataCreator;
import org.camunda.optimize.service.es.report.util.creator.max.MaxProcessInstanceDurationByNoneWithProcessPartReportDataCreator;
import org.camunda.optimize.service.es.report.util.creator.median.MedianProcessInstanceDurationByNoneWithProcessPartReportDataCreator;
import org.camunda.optimize.service.es.report.util.creator.min.MinProcessInstanceDurationByNoneWithProcessPartReportDataCreator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryRequestBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  private static final String PROCESS_DEFINITION_KEY = "123";
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
      START_EVENT,
      END_EVENT
    );
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
    assertThat(resultReportDataDto.getProcessPart(), is(notNullValue()));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(1000L));
  }

  private Object[] parametersForReportEvaluationForOneProcess() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), VIEW_AVERAGE_OPERATION},
      new Object[]{new MinProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), VIEW_MIN_OPERATION},
      new Object[]{new MaxProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), VIEW_MAX_OPERATION},
      new Object[]{new MedianProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), VIEW_MEDIAN_OPERATION}
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
    SingleReportDataDto reportDataDto =
      creator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );
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
    assertThat(resultReportDataDto.getProcessPart(), is(notNullValue()));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(1000L));
  }

  private Object[] parametersForReportEvaluationById() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), VIEW_AVERAGE_OPERATION},
      new Object[]{new MinProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), VIEW_MIN_OPERATION},
      new Object[]{new MaxProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), VIEW_MAX_OPERATION},
      new Object[]{new MedianProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), VIEW_MEDIAN_OPERATION}
    };
  }

  @Test
  @Parameters
  public void evaluateReportForMultipleEvents(ReportDataCreator reportDataCreator, Long expectedDuration) throws Exception {
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
    SingleReportDataDto reportData =
      reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT);
    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(expectedDuration));
  }

  private Object[] parametersForEvaluateReportForMultipleEvents() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), 4000L},
      new Object[]{new MinProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), 1000L},
      new Object[]{new MaxProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), 9000L},
      new Object[]{new MedianProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), 2000L}
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
        START_LOOP,
        END_LOOP);
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
  @Parameters(source = ReportDataCreatorProvider.class)
  public void activityHasNullDates(ReportDataCreator reportDataCreator) {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    setActivityStartDatesToNull();

    // when
    SingleReportDataDto reportData =
      reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT);
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
  @Parameters(source = ReportDataCreatorProvider.class)
  public void firstOccurrenceOfEndDateIsBeforeFirstOccurrenceOfStartDate(ReportDataCreator reportDataCreator) throws
                                                                                                              Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeFirstActivityInstanceStartDate(START_EVENT, startDate);
    engineDatabaseRule.changeFirstActivityInstanceEndDate(END_EVENT, startDate.minusSeconds(2));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData =
      reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
      );
    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(0L));
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
        "foo",
        END_EVENT);
    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(0L));
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
        START_EVENT,
        "FOo");
    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(0L));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void noAvailableProcessInstancesReturnsZero(ReportDataCreator reportDataCreator) {
    // when
    SingleReportDataDto reportData =
      reportDataCreator.create(
        "fooProcessDefinition",
        "1",
        START_EVENT,
        END_EVENT);
    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(0L));
  }

  @Test
  @Parameters
  public void reportAcrossAllVersions(ReportDataCreator reportDataCreator, Long expectedDuration) throws Exception {
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
    SingleReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        ReportConstants.ALL_VERSIONS,
        START_EVENT,
        END_EVENT
    );
    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(expectedDuration));
  }

  private Object[] parametersForReportAcrossAllVersions() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), 4000L},
      new Object[]{new MinProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), 1000L},
      new Object[]{new MaxProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), 9000L},
      new Object[]{new MedianProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), 2000L}
    };
  }

  @Test
  @Parameters
  public void otherProcessDefinitionsDoNoAffectResult(ReportDataCreator reportDataCreator, Long expectedDuration) throws Exception {
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
    SingleReportDataDto reportData = reportDataCreator.create(
        processDefinitionKey,
        processDefinitionVersion,
        START_EVENT,
        END_EVENT
    );
    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(expectedDuration));
  }

  private Object[] parametersForOtherProcessDefinitionsDoNoAffectResult() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), 4000L},
      new Object[]{new MinProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), 1000L},
      new Object[]{new MaxProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), 9000L},
      new Object[]{new MedianProcessInstanceDurationByNoneWithProcessPartReportDataCreator(), 2000L}
    };
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void filterInReportWorks(ReportDataCreator reportDataCreator) throws Exception {
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
    SingleReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        START_EVENT,
        END_EVENT
    );
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

  public static class ReportDataCreatorProvider {
    public static Object[] provideReportDataCreator() {
      return new Object[]{
        new Object[]{new AvgProcessInstanceDurationByNoneWithProcessPartReportDataCreator()},
        new Object[]{new MinProcessInstanceDurationByNoneWithProcessPartReportDataCreator()},
        new Object[]{new MaxProcessInstanceDurationByNoneWithProcessPartReportDataCreator()},
        new Object[]{new MedianProcessInstanceDurationByNoneWithProcessPartReportDataCreator()}
      };
    }
  }

}
