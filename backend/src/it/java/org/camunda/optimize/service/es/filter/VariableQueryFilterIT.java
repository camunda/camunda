package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.*;
import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataSingleReportResultDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.VariableFilterUtilHelper;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.*;
import static org.camunda.optimize.service.util.VariableHelper.*;
import static org.camunda.optimize.test.util.ReportDataHelper.createReportDataViewRawAsTable;
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

  private final String[] NUMERIC_TYPES =
    {INTEGER_TYPE, SHORT_TYPE, LONG_TYPE, DOUBLE_TYPE};

  private RawDataSingleReportResultDto evaluateReport(SingleReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    MatcherAssert.assertThat(response.getStatus(), is(200));
    return response.readEntity(RawDataSingleReportResultDto.class);
  }

  private Response evaluateReportWithFilterAndGetResponse(String processDefinitionKey, VariableFilterDto dto) {
    List<FilterDto> filterList = new ArrayList<>();
    filterList.add(dto);

    SingleReportDataDto reportData = createReportDataViewRawAsTable(processDefinitionKey, "1");
    reportData.setFilter(filterList);
    return evaluateReportAndReturnResponse(reportData);
  }

  private Response evaluateReportAndReturnResponse(SingleReportDataDto reportData) {
    return embeddedOptimizeRule.target("report/evaluate/single")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(reportData));
  }

  private RawDataSingleReportResultDto evaluateReportWithFilter(ProcessDefinitionEngineDto processDefinition,
                                                                VariableFilterDto filter) {
    List<FilterDto> filterList = new ArrayList<>();
    filterList.add(filter);
    return this.evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filterList);
  }

  private RawDataSingleReportResultDto evaluateReportWithFilter(String processDefinitionKey,
                                                                String processDefinitionVersion,
                                                                List<FilterDto> filter) {
    SingleReportDataDto reportData =
      createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
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
    VariableFilterDto filter = VariableFilterUtilHelper.createStringVariableFilter("var", IN, "value");
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = VariableFilterUtilHelper.createStringVariableFilter("stringVar", NOT_IN, "aStringValue");
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = VariableFilterUtilHelper.createStringVariableFilter("stringVar", IN, "aStringValue");
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result,  1);
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
    VariableFilterDto filter = VariableFilterUtilHelper.createStringVariableFilter("anotherStringVar", NOT_IN, "aStringValue");
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result,  2);
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
    VariableFilterDto filter = VariableFilterUtilHelper.createStringVariableFilter("stringVar", IN, "aStringValue");
    StringVariableFilterDataDto filterData = (StringVariableFilterDataDto) filter.getData();
    filterData.getData().getValues().add("anotherValue");
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = VariableFilterUtilHelper.createStringVariableFilter("stringVar", IN, "value");
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = VariableFilterUtilHelper.createStringVariableFilter("var", IN, "1");
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = VariableFilterUtilHelper.createStringVariableFilter("var", NOT_IN, "value");
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = VariableFilterUtilHelper.createStringVariableFilter("var", NOT_IN, "1");
    StringVariableFilterDataDto filterData = (StringVariableFilterDataDto) filter.getData();
    filterData.getData().getValues().add("2");
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = VariableFilterUtilHelper.createBooleanVariableFilter("var", "false");
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = VariableFilterUtilHelper.createBooleanVariableFilter("var", "true");
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void numericLessThanVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
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
      RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    for (String variableType : NUMERIC_TYPES) {
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
      RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void multipleNumericInequalityVariableFilter() throws Exception {
    for (String variableType : NUMERIC_TYPES) {
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
      RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result,  1);

      resetIndexesAndClean();
    }

  }

  @Test
  public void numericLessThanEqualVariableFilter() throws Exception {

    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
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
      RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericGreaterThanVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
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
      RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericGreaterThanEqualVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
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
      RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericEqualVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
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
      RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 1);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericUnequalVariableFilter() throws Exception {
    for (String variableType : NUMERIC_TYPES) {
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
      RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericWithinRangeVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
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
      List<FilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
      RawDataSingleReportResultDto result =
        evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

      // then
      assertResults(result, 1);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericOffRangeVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
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
      List<FilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
      RawDataSingleReportResultDto result =
        evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);
      // then
      assertResults(result, 0);
      elasticSearchRule.cleanAndVerify();
    }
  }

  @Test
  public void dateLessThanVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", nowDateMinusSeconds(2));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", nowDateMinusSeconds(1));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", nowDatePlus10Seconds());
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = VariableFilterUtilHelper.createDateVariableFilter("var", null, OffsetDateTime.now());
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void dateGreaterThanVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", nowDate());
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    OffsetDateTime nowMinusTwoSeconds = nowDateMinusSeconds(2);
    variables.put("var", nowMinusTwoSeconds);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", nowDatePlus10Seconds());
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter =
        VariableFilterUtilHelper.createDateVariableFilter("var", OffsetDateTime.now().minusSeconds(2L), null);
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void dateEqualVariableFilter() throws Exception {
    // given
    OffsetDateTime now = nowDate();
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", nowDateMinusSeconds(2));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", nowDatePlus10Seconds());
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = VariableFilterUtilHelper.createDateVariableFilter("var", now, now);
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void dateWithinRangeVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime nowMinus2Seconds = nowDateMinusSeconds(2);
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", nowDate());
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", nowMinus2Seconds);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = VariableFilterUtilHelper.createDateVariableFilter("var", now.minusSeconds(1L), now.plusSeconds(10L));
    RawDataSingleReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    variables.put("var", nowDateMinusSeconds(2));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", nowDatePlus10Seconds());
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = VariableFilterUtilHelper.createDateVariableFilter("var", now.minusSeconds(2L), now.minusSeconds(1L));
    VariableFilterDto filter2 = VariableFilterUtilHelper.createDateVariableFilter("var", now.plusSeconds(1L), now.plusSeconds(2L));
    List<FilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
    RawDataSingleReportResultDto result =
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
    assertThat(response.getStatus(),is(500));
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
    assertThat(response.getStatus(),is(500));
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
    assertThat(response.getStatus(),is(500));
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
    assertThat(response.getStatus(),is(500));
  }


  private OffsetDateTime nowDate() {
    return OffsetDateTime.now();
  }


  private OffsetDateTime nowDateMinusSeconds(int nSeconds) {
    return OffsetDateTime.now().minusSeconds(nSeconds);
  }

  private OffsetDateTime nowDatePlus10Seconds() {
    return OffsetDateTime.now().plusSeconds(10);
  }

  private VariableFilterDto createNumericVariableFilter(String operator, String variableName, String variableType, String variableValue) {
    switch (variableType.toLowerCase()) {
      case "integer":
        return VariableFilterUtilHelper.createIntegerVariableFilter(variableName, operator, variableValue);
      case "long":
        return VariableFilterUtilHelper.createLongVariableFilter(variableName, operator, variableValue);
      case "double":
        return VariableFilterUtilHelper.createDoubleVariableFilter(variableName, operator, variableValue);
      case "short":
        return VariableFilterUtilHelper.createShortVariableFilter(variableName, operator, variableValue);
    }
    return null;
  }

  private Object changeNumericValueToType(int value, String type) {
    switch (type.toLowerCase()) {
      case "integer":
        return value;
      case "long":
        return (long) value;
      case "short":
        return (short) value;
      case "double":
        return (double) value;
    }
    return value;
  }

  private void assertResults(RawDataSingleReportResultDto report, int piCount) {
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
