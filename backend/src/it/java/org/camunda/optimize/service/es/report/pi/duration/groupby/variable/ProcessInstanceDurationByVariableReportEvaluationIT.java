package org.camunda.optimize.service.es.report.pi.duration.groupby.variable;

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
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.MapProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewOperation;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.VariableHelper;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.camunda.optimize.test.util.ReportDataBuilder;
import org.camunda.optimize.test.util.ReportDataType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
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
  public void simpleReportEvaluation(ReportDataType reportDataType, ProcessViewOperation operation) throws SQLException {
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
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setVariableName("foo")
            .setVariableType("String")
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
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.VARIABLE));
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
      new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_VARIABLE, ProcessViewOperation.AVG},
      new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_VARIABLE, ProcessViewOperation.MIN},
      new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_VARIABLE, ProcessViewOperation.MAX},
      new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_VARIABLE, ProcessViewOperation.MEDIAN}
    };
  }

  @Test
  @Parameters
  public void simpleReportEvaluationById(ReportDataType reportDataType, ProcessViewOperation operation) throws SQLException {
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
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
            .setVariableName("foo")
            .setVariableType("String")
            .build();

    String reportId = createAndStoreDefaultReportDefinition(reportData);

    // when
    MapProcessReportResultDto result = evaluateReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(operation));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.VARIABLE));
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
      new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_VARIABLE, ProcessViewOperation.AVG},
      new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_VARIABLE, ProcessViewOperation.MIN},
      new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_VARIABLE, ProcessViewOperation.MAX},
      new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_VARIABLE, ProcessViewOperation.MEDIAN}
    };
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void reportAcrossAllVersions(ReportDataType reportDataType) throws SQLException {
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
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
            .setVariableName("foo")
            .setVariableType("String")
            .build();
    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(ALL_VERSIONS));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(2));
    assertThat(variableValueToCount.get("bar"), is(1000L));
    assertThat(variableValueToCount.get("bar2"), is(1000L));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void otherProcessDefinitionsDoNoAffectResult(ReportDataType reportDataType) throws SQLException {
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
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setVariableName("foo")
            .setVariableType("String")
            .build();
    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get("bar2"), is(1000L));
  }

  @Test
  @Parameters
  public void multipleProcessInstances(ReportDataType reportDataType,
                                       long firstVariableDuration,
                                       long secondVariableDuration) throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessDefinitionEngineDto processDefinitionDto = deploySimpleServiceTaskProcess();
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", "bar2");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 9);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 2);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processDefinitionDto.getKey())
            .setProcessDefinitionVersion(String.valueOf(processDefinitionDto.getVersion()))
            .setVariableName("foo")
            .setVariableType("String")
            .build();

    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinitionDto.getKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(String.valueOf(processDefinitionDto.getVersion())));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(2));
    assertThat(variableValueToCount.get("bar1"), is(firstVariableDuration));
    assertThat(variableValueToCount.get("bar2"), is(secondVariableDuration));
  }

  private Object[] parametersForMultipleProcessInstances() {
    return new Object[]{
      new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_VARIABLE, 1000L, 4000L},
      new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_VARIABLE, 1000L, 1000L},
      new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_VARIABLE, 1000L, 9000L},
      new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_VARIABLE, 1000L, 2000L}
    };
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void noAvailableDurationReturnsZero(ReportDataType reportDataType) {
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
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setVariableName("foo")
            .setVariableType("String")
            .build();
    MapProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().get("bar"), is(0L));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void variableTypeIsImportant(ReportDataType reportDataType) throws SQLException {
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
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setVariableName("foo")
            .setVariableType("String")
            .build();
    MapProcessReportResultDto result = evaluateReport(reportData);

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
  public void otherVariablesDoNotDistortTheResult(ReportDataType reportDataType) throws SQLException {
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
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
            .setVariableName("foo1")
            .setVariableType("String")
            .build();
    MapProcessReportResultDto result = evaluateReport(reportData);

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
  public void worksWithAllVariableTypes(ReportDataType reportDataType) throws SQLException {
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
      ProcessReportDataDto reportData = ReportDataBuilder
              .createReportData()
              .setReportDataType(reportDataType)
              .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
              .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
              .setVariableName(entry.getKey())
              .setVariableType(variableType)
              .build();

      MapProcessReportResultDto result = evaluateReport(reportData);

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
  @Parameters(source = ReportDataBuilderProvider.class)
  public void filterInReportWorks(ReportDataType reportDataType) throws SQLException {
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
    ProcessReportDataDto reportData = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
            .setVariableName("foo")
            .setVariableType("String")
            .build();

    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(null, startDate.minusSeconds(1L)));
    MapProcessReportResultDto result = evaluateReport(reportData);

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
  @Parameters(source = ReportDataBuilderProvider.class)
  public void optimizeExceptionOnViewEntityIsNull(ReportDataType reportDataType) {
    // given
    ProcessReportDataDto dataDto = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey("123")
            .setProcessDefinitionVersion("1")
            .setVariableName("foo")
            .setVariableType("String")
            .build();

    dataDto.getView().setEntity(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void optimizeExceptionOnViewPropertyIsNull(ReportDataType reportDataType) {
    // given
    ProcessReportDataDto dataDto = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey("123")
            .setProcessDefinitionVersion("1")
            .setVariableName("foo")
            .setVariableType("String")
            .build();

    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void optimizeExceptionOnGroupByTypeIsNull(ReportDataType reportDataType) {
    // given
    ProcessReportDataDto dataDto = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey("123")
            .setProcessDefinitionVersion("1")
            .setVariableName("foo")
            .setVariableType("String")
            .build();

    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void optimizeExceptionOnGroupByValueNameIsNull(ReportDataType reportDataType) {
    // given
    ProcessReportDataDto dataDto = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey("123")
            .setProcessDefinitionVersion("1")
            .setVariableName("foo")
            .setVariableType("String")
            .build();

    VariableGroupByDto groupByDto = (VariableGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setName(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  @Parameters(source = ReportDataBuilderProvider.class)
  public void optimizeExceptionOnGroupByValueTypeIsNull(ReportDataType reportDataType) {
    // given
    ProcessReportDataDto dataDto = ReportDataBuilder
            .createReportData()
            .setReportDataType(reportDataType)
            .setProcessDefinitionKey("123")
            .setProcessDefinitionVersion("1")
            .setVariableName("foo")
            .setVariableType("String")
            .build();

    VariableGroupByDto groupByDto = (VariableGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(Map<String, Object> variables) {
    return deployAndStartSimpleProcesses(variables).get(0);
  }

  private List<ProcessInstanceEngineDto> deployAndStartSimpleProcesses(Map<String, Object> variables) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    return IntStream.range(0, 1)
      .mapToObj( i -> {
        ProcessInstanceEngineDto processInstanceEngineDto =
          engineRule.startProcessInstance(processDefinition.getId(), variables);
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
  }

  private void startProcessInstanceShiftedBySeconds(Map<String, Object> variables,
                                                    String processDefinitionId,
                                                    int secondsToShift) throws SQLException {
    ProcessInstanceEngineDto processInstanceDto2;
    OffsetDateTime startDate;
    OffsetDateTime endDate;
    processInstanceDto2 = engineRule.startProcessInstance(processDefinitionId, variables);
    startDate = OffsetDateTime.now();
    endDate = startDate.plusSeconds(secondsToShift);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceDto2.getId(), endDate);
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

  private MapProcessReportResultDto evaluateReportById(String reportId) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSavedReportRequest(reportId)
            .execute(MapProcessReportResultDto.class, 200);
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

  public static class ReportDataBuilderProvider {
    public static Object[] provideReportDataCreator() {
      return new Object[]{
        new Object[]{ReportDataType.AVG_PROC_INST_DUR_GROUP_BY_VARIABLE},
        new Object[]{ReportDataType.MIN_PROC_INST_DUR_GROUP_BY_VARIABLE},
        new Object[]{ReportDataType.MAX_PROC_INST_DUR_GROUP_BY_VARIABLE},
        new Object[]{ReportDataType.MEDIAN_PROC_INST_DUR_GROUP_BY_VARIABLE}
      };
    }
  }
}
