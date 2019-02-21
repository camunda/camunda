package org.camunda.optimize.service.es.report.process.processinstance.frequency;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewOperation;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByVariable;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;


public class CountProcessInstanceFrequencyByVariableReportEvaluationIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();


  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void simpleReportEvaluation() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByVariable(
        processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
        VariableType.STRING
    );
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(ProcessViewOperation.COUNT));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.VARIABLE));
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName(), is("foo"));
    assertThat(variableGroupByDto.getValue().getType(), is(VariableType.STRING));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.get("bar"), is(1L));
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionKey,
                                                       String processDefinitionVersion,
                                                       String variableName,
                                                       VariableType variableType) {
    String id = createNewReport();
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByVariable(
        processDefinitionKey, processDefinitionVersion, variableName, variableType
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

  private ProcessReportMapResultDto evaluateReportById(String reportId) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSavedReportRequest(reportId)
            .execute(ProcessReportMapResultDto.class, 200);
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess(variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    String reportId = createAndStoreDefaultReportDefinition(
        processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      "foo",
        VariableType.STRING
    );

    // when
    ProcessReportMapResultDto result = evaluateReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(ProcessViewOperation.COUNT));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.VARIABLE));
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName(), is("foo"));
    assertThat(variableGroupByDto.getValue().getType(), is(VariableType.STRING));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    Map<String, Long> resultMap = result.getResult();
    assertThat(resultMap.get("bar"), is(1L));
  }

  @Test
  public void reportAcrossAllVersions() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    deployAndStartSimpleServiceTaskProcess(variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByVariable(
        processInstanceDto.getProcessDefinitionKey(),
        ALL_VERSIONS,
      "foo",
        VariableType.STRING
    );
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(ALL_VERSIONS));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(2));
    assertThat(variableValueToCount.get("bar"), is(1L));
    assertThat(variableValueToCount.get("bar2"), is(1L));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    deployAndStartSimpleServiceTaskProcess(variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByVariable(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
      "foo",
        VariableType.STRING
    );
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get("bar"), is(1L));
  }

  @Test
  public void multipleProcessInstances() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByVariable(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
      "foo",
        VariableType.STRING
    );
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(2));
    assertThat(variableValueToCount.get("bar1"), is(1L));
    assertThat(variableValueToCount.get("bar2"), is(2L));
  }

  @Test
  public void variableTypeIsImportant() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", 1);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByVariable(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
      "foo",
        VariableType.STRING
    );
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get("1"), is(1L));
  }

  @Test
  public void otherVariablesDoNotDistortTheResult() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo1", "bar1");
    variables.put("foo2", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByVariable(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
      "foo1",
        VariableType.STRING
    );
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get("bar1"), is(2L));
  }

  @Test
  public void worksWithAllVariableTypes() {
    // given
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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      VariableType variableType = varNameToTypeMap.get(entry.getKey());
      ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByVariable(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        entry.getKey(),
        variableType
      );
      ProcessReportMapResultDto result = evaluateReport(reportData);

      // then
      assertThat(result.getResult(), is(notNullValue()));
      Map<String, Long> variableValueToCount = result.getResult();
      assertThat(variableValueToCount.size(), is(1));
      if (VariableType.DATE.equals(variableType)) {
        OffsetDateTime temporal = (OffsetDateTime) variables.get(entry.getKey());
        String dateAsString = embeddedOptimizeRule.getDateTimeFormatter().format(
          // Note: we use UTC here as this is what we get back in the terms aggregation used
          // will get resolved with OPT-1713
          temporal.withOffsetSameLocal(ZoneOffset.UTC)
        );
        assertThat(variableValueToCount.get(dateAsString), is(1L));
      } else {
        assertThat(variableValueToCount.get(entry.getValue().toString()), is(1L));
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

  @Test
  public void dateFilterInReport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess(variables);
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByVariable(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
      "foo",
        VariableType.STRING
    );
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(null, past.minusSeconds(1L)));
    ProcessReportMapResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(0));

    // when
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(past, null));
    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    variableValueToCount = result.getResult();
    assertThat(variableValueToCount.size(), is(1));
    assertThat(variableValueToCount.get("bar"), is(1L));
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    ProcessReportDataDto dataDto = createCountProcessInstanceFrequencyGroupByVariable(
        "123",
        "1",
      "foo",
        VariableType.STRING
    );
    dataDto.getView().setEntity(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto = createCountProcessInstanceFrequencyGroupByVariable(
        "123",
        "1",
      "foo",
        VariableType.STRING
    );
    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = createCountProcessInstanceFrequencyGroupByVariable(
        "123",
        "1",
      "foo",
        VariableType.STRING
    );
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  public void optimizeExceptionOnGroupByValueNameIsNull() {
    // given
    ProcessReportDataDto dataDto = createCountProcessInstanceFrequencyGroupByVariable(
        "123",
        "1",
      "foo",
        VariableType.STRING
    );
    VariableGroupByDto groupByDto = (VariableGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setName(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByValueTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = createCountProcessInstanceFrequencyGroupByVariable(
        "123",
        "1",
      "foo",
        VariableType.STRING
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


}
