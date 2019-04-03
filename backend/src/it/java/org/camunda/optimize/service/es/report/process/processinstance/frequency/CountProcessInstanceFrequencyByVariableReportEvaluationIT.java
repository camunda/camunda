package org.camunda.optimize.service.es.report.process.processinstance.frequency;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByVariable;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
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
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.VARIABLE));
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName(), is("foo"));
    assertThat(variableGroupByDto.getValue().getType(), is(VariableType.STRING));

    final ProcessReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.get("bar"), is(1L));
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
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluationResponse = evaluateReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.VARIABLE));
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName(), is("foo"));
    assertThat(variableGroupByDto.getValue().getType(), is(VariableType.STRING));

    final ProcessReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    Map<String, Long> resultMap = result.getData();
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
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(ALL_VERSIONS));

    final ProcessReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getData();
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
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));

    final ProcessReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getData();
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
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));

    final ProcessReportMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getIsComplete(), is(true));
    assertThat(resultDto.getData(), is(notNullValue()));
    Map<String, Long> variableValueToCount = resultDto.getData();
    assertThat(variableValueToCount.size(), is(2));
    assertThat(variableValueToCount.get("bar1"), is(1L));
    assertThat(variableValueToCount.get("bar2"), is(2L));
  }

  @Test
  public void multipleBuckets_resultLimitedByConfig() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByVariable(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));

    final ProcessReportMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getProcessInstanceCount(), is(3L));
    assertThat(resultDto.getData(), is(notNullValue()));
    assertThat(resultDto.getData().size(), is(1));
    assertThat(resultDto.getIsComplete(), is(false));
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    variables.put("foo", "bar3");
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByVariable(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    assertThat(
      new ArrayList<>(resultMap.keySet()),
      // expect ascending order
      contains(new ArrayList<>(resultMap.keySet()).stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    variables.put("foo", "bar3");
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByVariable(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    final List<Long> bucketValues = new ArrayList<>(resultMap.values());
    assertThat(
      new ArrayList<>(bucketValues),
      contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
    );
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
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));

    final ProcessReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getData();
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
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));

    final ProcessReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getData();
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
      ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

      // then
      assertThat(result.getData(), is(notNullValue()));
      Map<String, Long> variableValueToCount = result.getData();
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
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(past.minusSeconds(1L))
                           .add()
                           .buildList());
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> variableValueToCount = result.getData();
    assertThat(variableValueToCount.size(), is(0));

    // when
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(past).end(null).add().buildList());
    result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    variableValueToCount = result.getData();
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

  private ProcessReportEvaluationResultDto<ProcessReportMapResultDto>  evaluateReportById(String reportId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessReportMapResultDto>>() {});
      // @formatter:on
  }

  private ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluateReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessReportMapResultDto>>() {});
      // @formatter:on
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSingleUnsavedReportRequest(reportData)
            .execute();
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
}
