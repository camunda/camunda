package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.test.it.Engine78;
import org.camunda.optimize.test.it.rule.ElasticsearchIntegrationRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.IN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.NOT_IN;
import static org.camunda.optimize.service.util.VariableHelper.BOOLEAN_TYPE;
import static org.camunda.optimize.service.util.VariableHelper.DATE_TYPE;
import static org.camunda.optimize.service.util.VariableHelper.DOUBLE_TYPE;
import static org.camunda.optimize.service.util.VariableHelper.INTEGER_TYPE;
import static org.camunda.optimize.service.util.VariableHelper.LONG_TYPE;
import static org.camunda.optimize.service.util.VariableHelper.SHORT_TYPE;
import static org.camunda.optimize.service.util.VariableHelper.STRING_TYPE;
import static org.camunda.optimize.test.util.ReportDataHelper.createReportDataViewRawAsTable;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class VariableQueryFilterIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticsearchIntegrationRule elasticSearchRule = new ElasticsearchIntegrationRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private final String TEST_DEFINITION_KEY = "testDefinition";

  private final String[] NUMERIC_TYPES =
    {INTEGER_TYPE, SHORT_TYPE, LONG_TYPE, DOUBLE_TYPE};

  private RawDataReportResultDto evaluateReport(ReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    MatcherAssert.assertThat(response.getStatus(), is(200));
    return response.readEntity(RawDataReportResultDto.class);
  }

  private Response evaluateReportWithFilterAndGetResponse(String processDefinitionKey, VariableFilterDto dto) {
    List<FilterDto> filterList = new ArrayList<>();
    filterList.add(dto);

    ReportDataDto reportData = createReportDataViewRawAsTable(processDefinitionKey, "1");
    reportData.setFilter(filterList);
    return evaluateReportAndReturnResponse(reportData);
  }

  private Response evaluateReportAndReturnResponse(ReportDataDto reportData) {
    return embeddedOptimizeRule.target("report/evaluate")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(reportData));
  }

  private RawDataReportResultDto evaluateReportWithFilter(ProcessDefinitionEngineDto processDefinition, 
                                                          VariableFilterDto filter) {
    List<FilterDto> filterList = new ArrayList<>();
    filterList.add(filter);
    return this.evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filterList);
  }

  private RawDataReportResultDto evaluateReportWithFilter(String processDefinitionKey, 
                                                          String processDefinitionVersion, 
                                                          List<FilterDto> filter) {
    ReportDataDto reportData = 
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
    VariableFilterDto filter = createVariableFilter(IN, "var", STRING_TYPE, "value");
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = createVariableFilter(NOT_IN, "stringVar", STRING_TYPE, "aStringValue");
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = createVariableFilter(IN, "stringVar", STRING_TYPE, "aStringValue");
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = createVariableFilter(NOT_IN, "anotherStringVar", STRING_TYPE, "aStringValue");
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = createVariableFilter(IN, "stringVar", STRING_TYPE, "aStringValue");
    filter.getData().getValues().add("anotherValue");
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = createVariableFilter(IN, "stringVar", STRING_TYPE, "value");
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = createVariableFilter(IN, "var", STRING_TYPE, "1");
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = createVariableFilter(NOT_IN, "var", STRING_TYPE, "value");
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = createVariableFilter(NOT_IN, "var", STRING_TYPE, "1");
    filter.getData().getValues().add("2");
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = createVariableFilter(EQUALS, "var", BOOLEAN_TYPE, "false");
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
    VariableFilterDto filter = createVariableFilter(EQUALS, "var", BOOLEAN_TYPE, "true");
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void booleanVariableFilterWithUnsupportedOperator() throws Exception {
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
    VariableFilterDto filter = createVariableFilter(NOT_IN, "var", BOOLEAN_TYPE, "true");
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 3);
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
      VariableFilterDto filter = createVariableFilter(LESS_THAN, "var", variableType, "5");
      RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
      VariableFilterDto filter = createVariableFilter(IN, "var", variableType, "1");
      filter.getData().getValues().add("2");
      RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
      VariableFilterDto filter = createVariableFilter(NOT_IN, "var", variableType, "1");
      filter.getData().getValues().add("2");
      RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
      VariableFilterDto filter = createVariableFilter(LESS_THAN_EQUALS, "var", variableType, "2");
      RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
      VariableFilterDto filter = createVariableFilter(GREATER_THAN, "var", variableType, "1");
      RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
      VariableFilterDto filter = createVariableFilter(GREATER_THAN_EQUALS, "var", variableType, "2");
      RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
      VariableFilterDto filter = createVariableFilter(IN, "var", variableType, "2");
      RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
      VariableFilterDto filter = createVariableFilter(NOT_IN, "var", variableType, "2");
      RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

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
      VariableFilterDto filter = createVariableFilter(GREATER_THAN, "var", variableType, "1");
      VariableFilterDto filter2 = createVariableFilter(LESS_THAN, "var", variableType, "10");
      List<FilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
      RawDataReportResultDto result =
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
      VariableFilterDto filter = createVariableFilter(LESS_THAN, "var", variableType, "2");
      VariableFilterDto filter2 = createVariableFilter(GREATER_THAN, "var", variableType, "2");
      List<FilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
      RawDataReportResultDto result =
        evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);
      // then
      assertResults(result, 0);
      elasticSearchRule.cleanAndVerify();
    }
  }

  @Test
  @Category(Engine78.class)
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
    VariableFilterDto filter = createVariableFilter(LESS_THAN, "var", DATE_TYPE, nowDateAsString());
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  @Category(Engine78.class)
  public void dateLessThanEqualVariableFilter() throws Exception {
    // given
    OffsetDateTime now = nowDate();
    String nowAsString = embeddedOptimizeRule.format(now);
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
    VariableFilterDto filter = createVariableFilter(LESS_THAN_EQUALS, "var", DATE_TYPE, nowAsString);
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  @Category(Engine78.class)
  public void dateGreaterThanVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", nowDate());
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    OffsetDateTime nowMinusTwoSeconds = nowDateMinusSeconds(2);
    String nowMinusTwoSecondsAsString = embeddedOptimizeRule.format(nowMinusTwoSeconds);
    variables.put("var", nowMinusTwoSeconds);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", nowDatePlus10Seconds());
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(GREATER_THAN, "var", DATE_TYPE, nowMinusTwoSecondsAsString);
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  @Category(Engine78.class)
  public void dateGreaterThanEqualVariableFilter() throws Exception {
    // given
    OffsetDateTime now = nowDate();
    String nowAsString = embeddedOptimizeRule.format(now);
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
    VariableFilterDto filter = createVariableFilter(GREATER_THAN_EQUALS, "var", DATE_TYPE, nowAsString);
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  @Category(Engine78.class)
  public void dateEqualVariableFilter() throws Exception {
    // given
    OffsetDateTime now = nowDate();
    String nowAsString = embeddedOptimizeRule.format(now);
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
    VariableFilterDto filter = createVariableFilter(IN, "var", DATE_TYPE, nowAsString);
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  @Category(Engine78.class)
  public void dateUnequalVariableFilter() throws Exception {

    // given
    OffsetDateTime now = nowDate();
    String nowAsString = embeddedOptimizeRule.format(now);
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
    VariableFilterDto filter = createVariableFilter(NOT_IN, "var", DATE_TYPE, nowAsString);
    RawDataReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  @Category(Engine78.class)
  public void dateWithinRangeVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    OffsetDateTime nowMinus2Seconds = nowDateMinusSeconds(2);
    OffsetDateTime nowPlus10Seconds = nowDatePlus10Seconds();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", nowDate());
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", nowMinus2Seconds);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", nowPlus10Seconds);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(GREATER_THAN, "var", DATE_TYPE, embeddedOptimizeRule.format(nowMinus2Seconds));
    VariableFilterDto filter2 = createVariableFilter(LESS_THAN, "var", DATE_TYPE, embeddedOptimizeRule.format(nowPlus10Seconds));
    List<FilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
    RawDataReportResultDto result =
      evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

    // then
    assertResults(result, 1);
  }

  @Test
  @Category(Engine78.class)
  public void dateOffRangeVariableFilter() throws Exception {
    // given
    OffsetDateTime now = nowDate();
    String nowAsString = embeddedOptimizeRule.format(now);
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
    VariableFilterDto filter = createVariableFilter(LESS_THAN, "var", DATE_TYPE, nowAsString);
    VariableFilterDto filter2 = createVariableFilter(GREATER_THAN, "var", DATE_TYPE, nowAsString);
    List<FilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
    RawDataReportResultDto result =
      evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

    // then
    assertResults(result, 0);
  }

  @Test
  public void validationExceptionOnNullValueField() {

    //given

    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName("foo");
    data.setType("foo");
    data.setOperator("foo");
    data.setValues(null);
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    // when
    Response response = evaluateReportWithFilterAndGetResponse(TEST_DEFINITION_KEY, variableFilterDto);

    // then
    assertThat(response.getStatus(),is(500));
  }



  @Test
  public void validationExceptionOnNullTypeField() {

    //given

    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName("foo");
    data.setType(null);
    data.setOperator("foo");
    data.setValues(Collections.singletonList("foo"));
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

    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName(null);
    data.setType("foo");
    data.setOperator("foo");
    data.setValues(Collections.singletonList("foo"));
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    // when
    Response response = evaluateReportWithFilterAndGetResponse(TEST_DEFINITION_KEY, variableFilterDto);

    // then
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void validationExceptionOnNullOperatorField() {

    //given

    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName("foo");
    data.setType("foo");
    data.setOperator(null);
    data.setValues(Collections.singletonList("foo"));
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


  private String nowDateAsString() {
    return embeddedOptimizeRule.format(nowDate());
  }

  private OffsetDateTime nowDateMinusSeconds(int nSeconds) {
    return OffsetDateTime.now().minusSeconds(nSeconds);
  }

  private OffsetDateTime nowDatePlus10Seconds() {
    return OffsetDateTime.now().plusSeconds(10);
  }

  private VariableFilterDto createVariableFilter(String operator, String variableName, String variableType, String variableValue) {

    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName(variableName);
    data.setType(variableType);
    data.setOperator(operator);
    List<String> values = new ArrayList<>();
    values.add(variableValue);
    data.setValues(values);
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
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

  private void assertResults(RawDataReportResultDto report, int piCount) {
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
