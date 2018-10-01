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
import org.camunda.optimize.service.es.report.util.creator.avg.AvgProcessInstanceDurationByNoneReportDataCreator;
import org.camunda.optimize.service.es.report.util.creator.max.MaxProcessInstanceDurationByNoneReportDataCreator;
import org.camunda.optimize.service.es.report.util.creator.median.MedianProcessInstanceDurationByNoneReportDataCreator;
import org.camunda.optimize.service.es.report.util.creator.min.MinProcessInstanceDurationByNoneReportDataCreator;
import org.camunda.optimize.service.es.report.util.creator.ReportDataCreator;
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
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MEDIAN_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MIN_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_PROCESS_INSTANCE_ENTITY;
import static org.camunda.optimize.test.util.VariableFilterUtilHelper.createBooleanVariableFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
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
    SingleReportDataDto reportData = creator.create(processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion());
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
    assertThat(resultReportDataDto.getProcessPart(), is(nullValue()));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(1000L));
  }

  private Object[] parametersForReportEvaluationForOneProcess() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneReportDataCreator(), VIEW_AVERAGE_OPERATION},
      new Object[]{new MinProcessInstanceDurationByNoneReportDataCreator(), VIEW_MIN_OPERATION},
      new Object[]{new MaxProcessInstanceDurationByNoneReportDataCreator(), VIEW_MAX_OPERATION},
      new Object[]{new MedianProcessInstanceDurationByNoneReportDataCreator(), VIEW_MEDIAN_OPERATION}
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
    SingleReportDataDto reportDataDto =
      creator.create(processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion());
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
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(1000L));
  }

  private Object[] parametersForReportEvaluationById() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneReportDataCreator(), VIEW_AVERAGE_OPERATION},
      new Object[]{new MinProcessInstanceDurationByNoneReportDataCreator(), VIEW_MIN_OPERATION},
      new Object[]{new MaxProcessInstanceDurationByNoneReportDataCreator(), VIEW_MAX_OPERATION},
      new Object[]{new MedianProcessInstanceDurationByNoneReportDataCreator(), VIEW_MEDIAN_OPERATION}
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
    endDatesToUpdate.put(processInstanceDto3.getId(), startDate.plusSeconds(9));
    engineDatabaseRule.updateProcessInstanceEndDates(endDatesToUpdate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData =
      reportDataCreator.create(processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion());
    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(expectedDuration));
  }

  private Object[] parametersForEvaluateReportForMultipleEvents() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneReportDataCreator(), 4000L},
      new Object[]{new MinProcessInstanceDurationByNoneReportDataCreator(), 1000L},
      new Object[]{new MaxProcessInstanceDurationByNoneReportDataCreator(), 9000L},
      new Object[]{new MedianProcessInstanceDurationByNoneReportDataCreator(), 2000L}
    };
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void noAvailableProcessInstancesReturnsZero(ReportDataCreator reportDataCreator) {
    // when
    SingleReportDataDto reportData =
      reportDataCreator.create("fooProcessDefinition", "1");
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

    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(9));
    processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(2));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();


    // when
    SingleReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(), ReportConstants.ALL_VERSIONS
    );
    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(expectedDuration));
  }

  private Object[] parametersForReportAcrossAllVersions() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneReportDataCreator(), 4000L},
      new Object[]{new MinProcessInstanceDurationByNoneReportDataCreator(), 1000L},
      new Object[]{new MaxProcessInstanceDurationByNoneReportDataCreator(), 9000L},
      new Object[]{new MedianProcessInstanceDurationByNoneReportDataCreator(), 2000L}
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
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(9));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(2));
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = reportDataCreator.create(
        processDefinitionKey, processDefinitionVersion
    );
    NumberSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult(), is(expectedDuration));
  }

  private Object[] parametersForOtherProcessDefinitionsDoNoAffectResult() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationByNoneReportDataCreator(), 4000L},
      new Object[]{new MinProcessInstanceDurationByNoneReportDataCreator(), 1000L},
      new Object[]{new MaxProcessInstanceDurationByNoneReportDataCreator(), 9000L},
      new Object[]{new MedianProcessInstanceDurationByNoneReportDataCreator(), 2000L}
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
    SingleReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.setFilter(createVariableFilter("var", "true"));
    NumberSingleReportResultDto result = evaluateReport(reportData);

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
    SingleReportDataDto dataDto = reportDataCreator.create(PROCESS_DEFINITION_KEY, "1");
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
    SingleReportDataDto dataDto = reportDataCreator.create(PROCESS_DEFINITION_KEY, "1");
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
        new Object[]{new AvgProcessInstanceDurationByNoneReportDataCreator()},
        new Object[]{new MinProcessInstanceDurationByNoneReportDataCreator()},
        new Object[]{new MaxProcessInstanceDurationByNoneReportDataCreator()},
        new Object[]{new MedianProcessInstanceDurationByNoneReportDataCreator()}
      };
    }
  }

}
