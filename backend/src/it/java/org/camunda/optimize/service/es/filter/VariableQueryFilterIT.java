package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.VariableType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.LongVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessVariableFilterUtilHelper;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.IN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.NOT_IN;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class VariableQueryFilterIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private final String TEST_DEFINITION_KEY = "testDefinition";

  private RawDataProcessReportResultDto evaluateReport(ProcessReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    MatcherAssert.assertThat(response.getStatus(), is(200));
    return response.readEntity(RawDataProcessReportResultDto.class);
  }

  private Response evaluateReportWithFilterAndGetResponse(String processDefinitionKey, VariableFilterDto dto) {
    List<ProcessFilterDto> filterList = new ArrayList<>();
    filterList.add(dto);

    ProcessReportDataDto reportData = createProcessReportDataViewRawAsTable(processDefinitionKey, "1");
    reportData.setFilter(filterList);
    return evaluateReportAndReturnResponse(reportData);
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  private RawDataProcessReportResultDto evaluateReportWithFilter(ProcessDefinitionEngineDto processDefinition,
                                                                 VariableFilterDto filter) {
    List<ProcessFilterDto> filterList = new ArrayList<>();
    filterList.add(filter);
    return this.evaluateReportWithFilter(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion()),
      filterList
    );
  }

  private RawDataProcessReportResultDto evaluateReportWithFilter(String processDefinitionKey,
                                                                 String processDefinitionVersion,
                                                                 List<ProcessFilterDto> filter) {
    ProcessReportDataDto reportData =
      createProcessReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(filter);
    return evaluateReport(reportData);
  }

  @Test
  public void simpleVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "anotherValue");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createStringVariableFilter("var", IN, "value");
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }


  @Test
  public void severalVariablesInSameProcessInstanceShouldNotAffectFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    variables.put("anotherStringVar", "anotherValue");
    variables.put("boolVar", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createStringVariableFilter(
      "stringVar",
      NOT_IN,
      "aStringValue"
    );
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 0);
  }

  @Test
  public void stringEqualityFilterWithVariableOfDifferentType() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    variables.put("anotherStringVar", "anotherValue");
    variables.put("boolVar", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createStringVariableFilter(
      "stringVar",
      IN,
      "aStringValue"
    );
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void stringInequalityFilterWithVariableOfDifferentTypeAndProcessInstance() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    variables.put("boolVar", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("anotherStringVar", "aStringValue");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createStringVariableFilter(
      "anotherStringVar",
      NOT_IN,
      "aStringValue"
    );
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void severalStringValueFiltersAreConcatenated() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineRule.startProcessInstance(processDefinition.getId());
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("stringVar", "anotherValue");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createStringVariableFilter(
      "stringVar",
      IN,
      "aStringValue"
    );
    StringVariableFilterDataDto filterData = (StringVariableFilterDataDto) filter.getData();
    filterData.getData().getValues().add("anotherValue");
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void variablesWithDifferentNameAreFiltered() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "value");
    variables.put("anotherStringVar", "value");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createStringVariableFilter("stringVar", IN, "value");
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void variablesWithDifferentTypeAreFiltered() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", 1);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createStringVariableFilter("var", IN, "1");
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void stringInequalityVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "anotherValue");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "aThirdValue");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createStringVariableFilter("var", NOT_IN, "value");
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void multipleStringInequalityVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "2");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "3");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createStringVariableFilter("var", NOT_IN, "1");
    StringVariableFilterDataDto filterData = (StringVariableFilterDataDto) filter.getData();
    filterData.getData().getValues().add("2");
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void booleanTrueVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createBooleanVariableFilter("var", "false");
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void booleanFalseVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createBooleanVariableFilter("var", "true");
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void numericLessThanVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createNumericVariableFilter(LESS_THAN, "var", variableType, "5");
      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  private void resetIndexesAndClean() {
    embeddedOptimizeRule.resetImportStartIndexes();
  }

  @Test
  public void multipleNumericEqualityVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    // given
    for (VariableType variableType : VariableType.getNumericTypes()) {
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(3, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createNumericVariableFilter(IN, "var", variableType, "1");
      OperatorMultipleValuesVariableFilterDataDto filterData =
        (OperatorMultipleValuesVariableFilterDataDto) filter.getData();
      filterData.getData().getValues().add("2");
      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void multipleNumericInequalityVariableFilter() throws Exception {
    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(3, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createNumericVariableFilter(NOT_IN, "var", variableType, "1");
      OperatorMultipleValuesVariableFilterDataDto filterData =
        (OperatorMultipleValuesVariableFilterDataDto) filter.getData();
      filterData.getData().getValues().add("2");
      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 1);

      resetIndexesAndClean();
    }

  }

  @Test
  public void numericLessThanEqualVariableFilter() throws Exception {

    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createNumericVariableFilter(LESS_THAN_EQUALS, "var", variableType, "2");
      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericGreaterThanVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createNumericVariableFilter(GREATER_THAN, "var", variableType, "1");
      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericGreaterThanEqualVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createNumericVariableFilter(GREATER_THAN_EQUALS, "var", variableType, "2");
      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericEqualVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createNumericVariableFilter(IN, "var", variableType, "2");
      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 1);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericUnequalVariableFilter() throws Exception {
    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createNumericVariableFilter(NOT_IN, "var", variableType, "2");
      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericWithinRangeVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createNumericVariableFilter(GREATER_THAN, "var", variableType, "1");
      VariableFilterDto filter2 = createNumericVariableFilter(LESS_THAN, "var", variableType, "10");
      List<ProcessFilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
      RawDataProcessReportResultDto result =
        evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

      // then
      assertResults(result, 1);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericOffRangeVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createNumericVariableFilter(LESS_THAN, "var", variableType, "2");
      VariableFilterDto filter2 = createNumericVariableFilter(GREATER_THAN, "var", variableType, "2");
      List<ProcessFilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
      RawDataProcessReportResultDto result =
        evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);
      // then
      assertResults(result, 0);
      elasticSearchRule.cleanAndVerify();
    }
  }

  @Test
  public void dateLessThanOrEqualVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    final OffsetDateTime now = nowDate();
    variables.put("var", now.minusSeconds(2));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(1));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.plusSeconds(10));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createDateVariableFilter("var", null, now);
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 3);
  }

  @Test
  public void dateGreaterOrEqualThanVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    final OffsetDateTime now = nowDate();
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(2));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.plusSeconds(10));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter =
      ProcessVariableFilterUtilHelper.createDateVariableFilter("var", now.minusSeconds(1), null);
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void dateEqualVariableFilter() throws Exception {
    // given
    final OffsetDateTime now = nowDate();
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(2));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.plusSeconds(10));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createDateVariableFilter("var", now, now);
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void dateWithinRangeVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    OffsetDateTime now = nowDate();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(2));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createDateVariableFilter(
      "var",
      now.minusSeconds(1L),
      now.plusSeconds(10L)
    );
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void dateOffRangeVariableFilter() throws Exception {
    // given
    OffsetDateTime now = nowDate();
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(2));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.plusSeconds(10));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = ProcessVariableFilterUtilHelper.createDateVariableFilter(
      "var",
      now.minusSeconds(2L),
      now.minusSeconds(1L)
    );
    VariableFilterDto filter2 = ProcessVariableFilterUtilHelper.createDateVariableFilter(
      "var",
      now.plusSeconds(1L),
      now.plusSeconds(2L)
    );
    List<ProcessFilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
    RawDataProcessReportResultDto result =
      evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

    // then
    assertResults(result, 0);
  }

  @Test
  public void validationExceptionOnNullValueField() {

    //given
    BooleanVariableFilterDataDto data = new BooleanVariableFilterDataDto("true");
    data.setName("foo");
    data.getData().setValue(null);
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    // when
    Response response = evaluateReportWithFilterAndGetResponse(TEST_DEFINITION_KEY, variableFilterDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void validationExceptionOnNullOnAllDateValueField() {

    //given
    DateVariableFilterDataDto data = new DateVariableFilterDataDto(null, null);
    data.setName("foo");
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    // when
    Response response = evaluateReportWithFilterAndGetResponse(TEST_DEFINITION_KEY, variableFilterDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void validationExceptionOnNullNumericValuesField() {

    //given
    LongVariableFilterDataDto data = new LongVariableFilterDataDto(IN, null);
    data.setName("foo");
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    // when
    Response response = evaluateReportWithFilterAndGetResponse(TEST_DEFINITION_KEY, variableFilterDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void validationExceptionOnNullNameField() {

    //given
    BooleanVariableFilterDataDto data = new BooleanVariableFilterDataDto("true");
    data.setName(null);
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    // when
    Response response = evaluateReportWithFilterAndGetResponse(TEST_DEFINITION_KEY, variableFilterDto);

    // then
    assertThat(response.getStatus(), is(500));
  }


  private OffsetDateTime nowDate() {
    return OffsetDateTime.now();
  }

  private VariableFilterDto createNumericVariableFilter(String operator,
                                                        String variableName,
                                                        VariableType variableType,
                                                        String variableValue) {
    switch (variableType) {
      case INTEGER:
        return ProcessVariableFilterUtilHelper.createIntegerVariableFilter(variableName, operator, variableValue);
      case LONG:
        return ProcessVariableFilterUtilHelper.createLongVariableFilter(variableName, operator, variableValue);
      case DOUBLE:
        return ProcessVariableFilterUtilHelper.createDoubleVariableFilter(variableName, operator, variableValue);
      case SHORT:
        return ProcessVariableFilterUtilHelper.createShortVariableFilter(variableName, operator, variableValue);
    }
    return null;
  }

  private Object changeNumericValueToType(int value, VariableType type) {
    switch (type) {
      case INTEGER:
        return value;
      case LONG:
        return (long) value;
      case SHORT:
        return (short) value;
      case DOUBLE:
        return (double) value;
    }
    return value;
  }

  private void assertResults(RawDataProcessReportResultDto report, int piCount) {
    assertThat("PI count", report.getResult().size(), is(piCount));
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }


}
