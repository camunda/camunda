package org.camunda.optimize.service.es.report.pi.duration.groupby.none;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.result.NumberReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;
import org.camunda.optimize.service.es.report.util.AvgProcessInstanceDurationByNoneReportDataCreator;
import org.camunda.optimize.service.es.report.util.MaxProcessInstanceDurationByNoneReportDataCreator;
import org.camunda.optimize.service.es.report.util.MinProcessInstanceDurationByNoneReportDataCreator;
import org.camunda.optimize.service.es.report.util.ReportDataCreator;
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
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_NONE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_AVERAGE_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_DURATION_PROPERTY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MAX_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MIN_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_PROCESS_INSTANCE_ENTITY;
import static org.camunda.optimize.test.util.VariableFilterUtilHelper.createBooleanVariableFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public class ProcessInstanceDurationByNoneReportEvaluationIT {

  public static final String PROCESS_DEFINITION_KEY = "123";
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
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = creator.create(processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion());
    NumberReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_PROCESS_INSTANCE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_DURATION_PROPERTY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(GROUP_BY_NONE_TYPE));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(1000L));
  }

  private Object[] parametersForReportEvaluationForOneProcess() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneReportDataCreator(), VIEW_AVERAGE_OPERATION},
      new Object[]{new MinProcessInstanceDurationByNoneReportDataCreator(), VIEW_MIN_OPERATION},
      new Object[]{new MaxProcessInstanceDurationByNoneReportDataCreator(), VIEW_MAX_OPERATION}
    };
  }

  @Test
  @Parameters
  public void reportEvaluationById(ReportDataCreator creator, String operation) throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    ReportDataDto reportDataDto =
      creator.create(processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion());
    String reportId = createAndStoreDefaultReportDefinition(reportDataDto);

    // when
    NumberReportResultDto result = evaluateReportById(reportId);

    // then
    ReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));

    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_PROCESS_INSTANCE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_DURATION_PROPERTY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(GROUP_BY_NONE_TYPE));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(1000L));
  }

  private Object[] parametersForReportEvaluationById() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneReportDataCreator(), VIEW_AVERAGE_OPERATION},
      new Object[]{new MinProcessInstanceDurationByNoneReportDataCreator(), VIEW_MIN_OPERATION},
      new Object[]{new MaxProcessInstanceDurationByNoneReportDataCreator(), VIEW_MAX_OPERATION}
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
    engineDatabaseRule.updateProcessInstanceStartDates(startDatesToUpdate);
    Map<String, OffsetDateTime> endDatesToUpdate = new HashMap<>();
    endDatesToUpdate.put(processInstanceDto.getId(), startDate.plusSeconds(1));
    endDatesToUpdate.put(processInstanceDto2.getId(), startDate.plusSeconds(2));
    endDatesToUpdate.put(processInstanceDto3.getId(), startDate.plusSeconds(3));
    engineDatabaseRule.updateProcessInstanceEndDates(endDatesToUpdate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData =
      reportDataCreator.create(processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion());
    NumberReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(expectedDuration));
  }

  private Object[] parametersForEvaluateReportForMultipleEvents() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneReportDataCreator(), 2000L},
      new Object[]{new MinProcessInstanceDurationByNoneReportDataCreator(), 1000L},
      new Object[]{new MaxProcessInstanceDurationByNoneReportDataCreator(), 3000L}
    };
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void noAvailableProcessInstancesReturnsZero(ReportDataCreator reportDataCreator) {
    // when
    ReportDataDto reportData =
      reportDataCreator.create("fooProcessDefinition", "1");
    NumberReportResultDto result = evaluateReport(reportData);

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

    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(3));
    processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(2));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();


    // when
    ReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(), ReportConstants.ALL_VERSIONS
    );
    NumberReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(expectedDuration));
  }

  private Object[] parametersForReportAcrossAllVersions() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneReportDataCreator(), 2000L},
      new Object[]{new MinProcessInstanceDurationByNoneReportDataCreator(), 1000L},
      new Object[]{new MaxProcessInstanceDurationByNoneReportDataCreator(), 3000L}
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

    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(3));
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = reportDataCreator.create(
        processDefinitionKey, processDefinitionVersion
    );
    NumberReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(expectedDuration));
  }

  private Object[] parametersForOtherProcessDefinitionsDoNoAffectResult() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneReportDataCreator(), 2000L},
      new Object[]{new MinProcessInstanceDurationByNoneReportDataCreator(), 1000L},
      new Object[]{new MaxProcessInstanceDurationByNoneReportDataCreator(), 3000L}
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
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.setFilter(createVariableFilter("var", "true"));
    NumberReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(1000L));

    // when
    reportData.setFilter(createVariableFilter("var", "false"));
    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(0L));
  }

  private List<FilterDto> createVariableFilter(String varName, String value) {
    VariableFilterDto variableFilterDto = createBooleanVariableFilter(varName, value);
    return Collections.singletonList(variableFilterDto);
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void optimizeExceptionOnViewPropertyIsNull(ReportDataCreator reportDataCreator) {
    // given
    ReportDataDto dataDto = reportDataCreator.create(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void optimizeExceptionOnGroupByTypeIsNull(ReportDataCreator reportDataCreator) {
    // given
    ReportDataDto dataDto = reportDataCreator.create(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String activityId) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask(activityId)
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployAndStartProcess(processModel);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(Map<String, Object> variables) {
    return deployAndStartSimpleServiceTaskProcessWithVariables(TEST_ACTIVITY, variables);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(String activityId,
                                                                                       Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask(activityId)
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private NumberReportResultDto evaluateReport(ReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(NumberReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ReportDataDto reportData) {
    return embeddedOptimizeRule.target("report/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .post(Entity.json(reportData));
  }

  private String createAndStoreDefaultReportDefinition(ReportDataDto reportData) {
    String id = createNewReport();

    ReportDefinitionDto report = new ReportDefinitionDto();
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
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }

  private String createNewReport() {
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  private NumberReportResultDto evaluateReportById(String reportId) {
    Response response = embeddedOptimizeRule.target("report/" + reportId + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .get();
    assertThat(response.getStatus(), is(200));

    return response.readEntity(NumberReportResultDto.class);
  }

  public static class ReportDataCreatorProvider {
    public static Object[] provideReportDataCreator() {
      return new Object[]{
        new Object[]{new AvgProcessInstanceDurationByNoneReportDataCreator()},
        new Object[]{new MinProcessInstanceDurationByNoneReportDataCreator()},
        new Object[]{new MaxProcessInstanceDurationByNoneReportDataCreator()}
      };
    }
  }

}
