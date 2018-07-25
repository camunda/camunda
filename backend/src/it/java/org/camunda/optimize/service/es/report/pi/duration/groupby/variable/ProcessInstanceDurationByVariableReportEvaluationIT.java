package org.camunda.optimize.service.es.report.pi.duration.groupby.variable;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.result.MapReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.AvgProcessInstanceDurationGroupByVariableReportDataCreator;
import org.camunda.optimize.service.es.report.util.MaxProcessInstanceDurationGroupByVariableReportDataCreator;
import org.camunda.optimize.service.es.report.util.MinProcessInstanceDurationGroupByVariableReportDataCreator;
import org.camunda.optimize.service.es.report.util.ReportDataCreator;
import org.camunda.optimize.service.util.VariableHelper;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_VARIABLE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_AVERAGE_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_DURATION_PROPERTY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MAX_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MIN_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_PROCESS_INSTANCE_ENTITY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;


@RunWith(JUnitParamsRunner.class)
public class ProcessInstanceDurationByVariableReportEvaluationIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();


  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  @Parameters
  public void simpleReportEvaluation(ReportDataCreator reportDataCreator, String operation) throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      "String"
    );
    MapReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_PROCESS_INSTANCE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_DURATION_PROPERTY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(GROUP_BY_VARIABLE_TYPE));
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName(), is("foo"));
    assertThat(variableGroupByDto.getValue().getType(), is("String"));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.get("bar"), is(1000L));
  }

  private Object[] parametersForSimpleReportEvaluation() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationGroupByVariableReportDataCreator(), VIEW_AVERAGE_OPERATION},
      new Object[]{new MinProcessInstanceDurationGroupByVariableReportDataCreator(), VIEW_MIN_OPERATION},
      new Object[]{new MaxProcessInstanceDurationGroupByVariableReportDataCreator(), VIEW_MAX_OPERATION}
    };
  }

  @Test
  @Parameters
  public void simpleReportEvaluationById(ReportDataCreator reportDataCreator, String operation) throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstance.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstance.getId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    ReportDataDto reportData = reportDataCreator.create(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      "foo",
      "String"
    );
    String reportId = createAndStoreDefaultReportDefinition(reportData);

    // when
    MapReportResultDto result = evaluateReportById(reportId);

    // then
    ReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_PROCESS_INSTANCE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_DURATION_PROPERTY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(GROUP_BY_VARIABLE_TYPE));
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName(), is("foo"));
    assertThat(variableGroupByDto.getValue().getType(), is("String"));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.get("bar"), is(1000L));
  }

  private Object[] parametersForSimpleReportEvaluationById() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationGroupByVariableReportDataCreator(), VIEW_AVERAGE_OPERATION},
      new Object[]{new MinProcessInstanceDurationGroupByVariableReportDataCreator(), VIEW_MIN_OPERATION},
      new Object[]{new MaxProcessInstanceDurationGroupByVariableReportDataCreator(), VIEW_MAX_OPERATION}
    };
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void reportAcrossAllVersions(ReportDataCreator reportDataCreator) throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    variables.put("foo", "bar2");
    processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        ALL_VERSIONS,
      "foo",
      "String"
    );
    MapReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(ALL_VERSIONS));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(2));
    assertThat(variableValueToCount.get("bar"), is(1000L));
    assertThat(variableValueToCount.get("bar2"), is(1000L));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void otherProcessDefinitionsDoNoAffectResult(ReportDataCreator reportDataCreator) throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    variables.put("foo", "bar2");
    processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      "String"
    );
    MapReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get("bar2"), is(1000L));
  }

  @Test
  @Parameters
  public void multipleProcessInstances(ReportDataCreator reportDataCreator,
                                       long firstVariableDuration,
                                       long secondVariableDuration) throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    variables.put("foo", "bar2");
    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto2.getId(), endDate);
    processInstanceDto2 = engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    startDate = OffsetDateTime.now();
    endDate = startDate.plusSeconds(3);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto2.getId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      "String"
    );
    MapReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(2));
    assertThat(variableValueToCount.get("bar1"), is(firstVariableDuration));
    assertThat(variableValueToCount.get("bar2"), is(secondVariableDuration));
  }

  private Object[] parametersForMultipleProcessInstances() {
    return new Object[]{
      new Object[]{new AvgProcessInstanceDurationGroupByVariableReportDataCreator(), 1000L, 2000L},
      new Object[]{new MinProcessInstanceDurationGroupByVariableReportDataCreator(), 1000L, 1000L},
      new Object[]{new MaxProcessInstanceDurationGroupByVariableReportDataCreator(), 1000L, 3000L}
    };
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void noAvailableDurationReturnsZero(ReportDataCreator reportDataCreator) {
    // given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto =
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      "String"
    );
    MapReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().get("bar"), is(0L));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void variableTypeIsImportant(ReportDataCreator reportDataCreator) throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    variables.put("foo", 1);
    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto2.getId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      "String"
    );
    MapReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultReportDataDto = result.getData();
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
    variables.put("foo2", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto2.getId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
      "foo1",
      "String"
    );
    MapReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultReportDataDto = result.getData();
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
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      String variableType = varNameToTypeMap.get(entry.getKey());
      ReportDataDto reportData = reportDataCreator.create(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        entry.getKey(),
        variableType
      );
      MapReportResultDto result = evaluateReport(reportData);

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

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void filterInReportWorks(ReportDataCreator reportDataCreator) throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstance.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstance.getId(), endDate);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = reportDataCreator.create(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
      "foo",
      "String"
    );
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(null, startDate.minusSeconds(1L)));
    MapReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(0));

    // when
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(startDate, null));
    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get("bar"), is(1000L));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void optimizeExceptionOnViewEntityIsNull(ReportDataCreator reportDataCreator) {
    // given
    ReportDataDto dataDto = reportDataCreator.create(
        "123",
        "1",
      "foo",
      "String"
    );
    dataDto.getView().setEntity(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void optimizeExceptionOnViewPropertyIsNull(ReportDataCreator reportDataCreator) {
    // given
    ReportDataDto dataDto = reportDataCreator.create(
        "123",
        "1",
      "foo",
      "String"
    );
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
    ReportDataDto dataDto = reportDataCreator.create(
        "123",
        "1",
      "foo",
      "String"
    );
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void optimizeExceptionOnGroupByValueNameIsNull(ReportDataCreator reportDataCreator) {
    // given
    ReportDataDto dataDto = reportDataCreator.create(
        "123",
        "1",
      "foo",
      "String"
    );
    VariableGroupByDto groupByDto = (VariableGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setName(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  @Parameters(source = ReportDataCreatorProvider.class)
  public void optimizeExceptionOnGroupByValueTypeIsNull(ReportDataCreator reportDataCreator) {
    // given
    ReportDataDto dataDto = reportDataCreator.create(
        "123",
        "1",
      "foo",
      "String"
    );
    VariableGroupByDto groupByDto = (VariableGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(Map<String, Object> variables) {
    return deployAndStartSimpleProcesses(1, variables).get(0);
  }

  private List<ProcessInstanceEngineDto> deployAndStartSimpleProcesses(int number, Map<String, Object> variables) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    return IntStream.range(0, number)
      .mapToObj( i -> {
        ProcessInstanceEngineDto processInstanceEngineDto =
          engineRule.startProcessInstance(processDefinition.getId(), variables);
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

  private MapReportResultDto evaluateReport(ReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(MapReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ReportDataDto reportData) {
    return embeddedOptimizeRule.target("report/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .post(Entity.json(reportData));
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

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }

  private MapReportResultDto evaluateReportById(String reportId) {
    Response response = embeddedOptimizeRule.target("report/" + reportId + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .get();
    assertThat(response.getStatus(), is(200));

    return response.readEntity(MapReportResultDto.class);
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

  public static class ReportDataCreatorProvider {
    public static Object[] provideReportDataCreator() {
      return new Object[]{
        new Object[]{new AvgProcessInstanceDurationGroupByVariableReportDataCreator()},
        new Object[]{new MinProcessInstanceDurationGroupByVariableReportDataCreator()},
        new Object[]{new MaxProcessInstanceDurationGroupByVariableReportDataCreator()}
      };
    }
  }
}
