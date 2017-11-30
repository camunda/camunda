package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class VariableQueryFilterIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private SimpleDateFormat sdf;

  @Before
  public void init() {
    sdf = new SimpleDateFormat(elasticSearchRule.getDateFormat());
  }

  private final String TEST_DEFINITION = "testDefinition";

  private final String[] NUMERIC_TYPES =
    {INTEGER_TYPE, SHORT_TYPE, LONG_TYPE, DOUBLE_TYPE};

  @Test
  public void simpleVariableFilter() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "anotherValue");
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(IN, "var", STRING_TYPE, "value");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 1L);
  }

  @Test
  public void severalVariablesInSameProcessInstanceShouldNotAffectFilter() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    variables.put("anotherStringVar", "anotherValue");
    variables.put("boolVar", true);
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(NOT_IN, "stringVar", STRING_TYPE, "aStringValue");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 0, 0L);
  }

  @Test
  public void stringEqualityFilterWithVariableOfDifferentType() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    variables.put("anotherStringVar", "anotherValue");
    variables.put("boolVar", true);
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(IN, "stringVar", STRING_TYPE, "aStringValue");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 1L);
  }

  @Test
  public void stringInequalityFilterWithVariableOfDifferentTypeAndProcessInstance() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    variables.put("boolVar", true);
    engineRule.startProcessInstance(processDefinitionId, variables);
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("anotherStringVar", "aStringValue");
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(NOT_IN, "anotherStringVar", STRING_TYPE, "aStringValue");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 2L);
  }

  @Test
  public void severalStringValueFiltersAreConcatenated() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    engineRule.startProcessInstance(processDefinitionId);
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("stringVar", "anotherValue");
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(IN, "stringVar", STRING_TYPE, "aStringValue");
    filter.getData().getValues().add("anotherValue");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 2L);
  }

  @Test
  public void variablesWithDifferentNameAreFiltered() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "value");
    variables.put("anotherStringVar", "value");
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(IN, "stringVar", STRING_TYPE, "value");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 1L);
  }

  @Test
  public void variablesWithDifferentTypeAreFiltered() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "1");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", 1);
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(IN, "var", STRING_TYPE, "1");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 1L);
  }

  @Test
  public void stringInequalityVariableFilter() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "anotherValue");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "aThirdValue");
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(NOT_IN, "var", STRING_TYPE, "value");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 2L);
  }

  @Test
  public void multipleStringInequalityVariableFilter() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "1");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "2");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "3");
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(NOT_IN, "var", STRING_TYPE, "1");
    filter.getData().getValues().add("2");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 1L);
  }

  @Test
  public void booleanTrueVariableFilter() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", false);
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", false);
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(EQUALS, "var", BOOLEAN_TYPE, "false");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 2L);
  }

  @Test
  public void booleanFalseVariableFilter() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", true);
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", false);
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(EQUALS, "var", BOOLEAN_TYPE, "true");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 2L);
  }

  @Test
  public void booleanVariableFilterWithUnsupportedOperator() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", false);
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", false);
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(NOT_IN, "var", BOOLEAN_TYPE, "true");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 3L);
  }

  @Test
  public void numericLessThanVariableFilter() throws Exception {
    String processDefinitionId = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createVariableFilter(LESS_THAN, "var", variableType, "5");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

      // then
      assertResults(testDefinition, 2, 2L);

      resetIndexesAndClean();
    }
  }

  private void resetIndexesAndClean() {
    embeddedOptimizeRule.resetImportStartIndexes();
  }

  @Test
  public void multipleNumericEqualityVariableFilter() throws Exception {
    String processDefinitionId = deploySimpleProcessDefinition();

    // given
    for (String variableType : NUMERIC_TYPES) {
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(3, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createVariableFilter(IN, "var", variableType, "1");
      filter.getData().getValues().add("2");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

      // then
      assertResults(testDefinition, 2, 2L);

      resetIndexesAndClean();
    }
  }

  @Test
  public void multipleNumericInequalityVariableFilter() throws Exception {
    for (String variableType : NUMERIC_TYPES) {
      // given
      String processDefinitionId = deploySimpleProcessDefinition();

      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(3, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createVariableFilter(NOT_IN, "var", variableType, "1");
      filter.getData().getValues().add("2");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

      // then
      assertResults(testDefinition, 2, 1L);
    }

  }

  @Test
  public void numericLessThanEqualVariableFilter() throws Exception {

    String processDefinitionId = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createVariableFilter(LESS_THAN_EQUALS, "var", variableType, "2");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

      // then
      assertResults(testDefinition, 2, 2L);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericGreaterThanVariableFilter() throws Exception {
    String processDefinitionId = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createVariableFilter(GREATER_THAN, "var", variableType, "1");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

      // then
      assertResults(testDefinition, 2, 2L);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericGreaterThanEqualVariableFilter() throws Exception {
    String processDefinitionId = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createVariableFilter(GREATER_THAN_EQUALS, "var", variableType, "2");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

      // then
      assertResults(testDefinition, 2, 2L);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericEqualVariableFilter() throws Exception {
    String processDefinitionId = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createVariableFilter(IN, "var", variableType, "2");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

      // then
      assertResults(testDefinition, 2, 1L);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericUnequalVariableFilter() throws Exception {
    for (String variableType : NUMERIC_TYPES) {
      // given
      String processDefinitionId = deploySimpleProcessDefinition();

      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createVariableFilter(NOT_IN, "var", variableType, "2");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

      // then
      assertResults(testDefinition, 2, 2L);
    }
  }

  @Test
  public void numericWithinRangeVariableFilter() throws Exception {
    String processDefinitionId = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createVariableFilter(GREATER_THAN, "var", variableType, "1");
      VariableFilterDto filter2 = createVariableFilter(LESS_THAN, "var", variableType, "10");
      List<FilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilters(processDefinitionId, filters);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

      // then
      assertResults(testDefinition, 2, 1L);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericOffRangeVariableFilter() throws Exception {
    String processDefinitionId = deploySimpleProcessDefinition();

    for (String variableType : NUMERIC_TYPES) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinitionId, variables);
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();

      // when
      VariableFilterDto filter = createVariableFilter(LESS_THAN, "var", variableType, "2");
      VariableFilterDto filter2 = createVariableFilter(GREATER_THAN, "var", variableType, "2");
      List<FilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilters(processDefinitionId, filters);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

      // then
      assertResults(testDefinition, 0, 0L);
      elasticSearchRule.cleanAndVerify();
    }
  }

  @Test
  public void dateLessThanVariableFilter() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", nowDateMinusSeconds(2));
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowDateMinusSeconds(1));
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowDatePlus10Seconds());
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(LESS_THAN, "var", DATE_TYPE, nowDateAsString());
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 2L);
  }

  @Test
  public void dateLessThanEqualVariableFilter() throws Exception {
    // given
    Date now = nowDate();
    String nowAsString = sdf.format(now);
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", nowDateMinusSeconds(2));
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowDateMinusSeconds(1));
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowDatePlus10Seconds());
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(LESS_THAN_EQUALS, "var", DATE_TYPE, nowAsString);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 2L);
  }

  @Test
  public void dateGreaterThanVariableFilter() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", nowDate());
    engineRule.startProcessInstance(processDefinitionId, variables);
    Date nowMinusTwoSeconds = nowDateMinusSeconds(2);
    String nowMinusTwoSecondsAsString = sdf.format(nowMinusTwoSeconds);
    variables.put("var", nowMinusTwoSeconds);
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowDatePlus10Seconds());
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(GREATER_THAN, "var", DATE_TYPE, nowMinusTwoSecondsAsString);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 2L);
  }
  @Test
  public void dateGreaterThanEqualVariableFilter() throws Exception {
    // given
    Date now = nowDate();
    String nowAsString = sdf.format(now);
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowDateMinusSeconds(2));
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowDatePlus10Seconds());
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(GREATER_THAN_EQUALS, "var", DATE_TYPE, nowAsString);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 2L);
  }

  @Test
  public void dateEqualVariableFilter() throws Exception {
    // given
    Date now = nowDate();
    String nowAsString = sdf.format(now);
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowDateMinusSeconds(2));
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowDatePlus10Seconds());
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(IN, "var", DATE_TYPE, nowAsString);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 1L);
  }

  @Test
  public void dateUnequalVariableFilter() throws Exception {

    // given
    Date now = nowDate();
    String nowAsString = sdf.format(now);
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowDateMinusSeconds(2));
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowDatePlus10Seconds());
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(NOT_IN, "var", DATE_TYPE, nowAsString);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 2L);
  }

  @Test
  public void dateWithinRangeVariableFilter() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Date nowMinus2Seconds = nowDateMinusSeconds(2);
    Date nowPlus10Seconds = nowDatePlus10Seconds();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", nowDate());
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowMinus2Seconds);
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowPlus10Seconds);
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(GREATER_THAN, "var", DATE_TYPE, sdf.format(nowMinus2Seconds));
    VariableFilterDto filter2 = createVariableFilter(LESS_THAN, "var", DATE_TYPE, sdf.format(nowPlus10Seconds));
    List<FilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilters(processDefinitionId, filters);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 2, 1L);
  }

  @Test
  public void dateOffRangeVariableFilter() throws Exception {
    // given
    Date now = nowDate();
    String nowAsString = sdf.format(now);
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowDateMinusSeconds(2));
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", nowDatePlus10Seconds());
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter(LESS_THAN, "var", DATE_TYPE, nowAsString);
    VariableFilterDto filter2 = createVariableFilter(GREATER_THAN, "var", DATE_TYPE, nowAsString);
    List<FilterDto> filters = Stream.of(filter, filter2).collect(Collectors.toList());
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilters(processDefinitionId, filters);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, 0, 0L);
  }

  @Test
  public void validationExceptionOnNullValueField() {

    //given
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(TEST_DEFINITION);

    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName("foo");
    data.setType("foo");
    data.setOperator("foo");
    data.setValues(null);
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    dto.setFilter(Collections.singletonList(variableFilterDto));

    // when
    Response response = getResponse(dto);

    // then
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void validationExceptionOnNullTypeField() {

    //given
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(TEST_DEFINITION);

    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName("foo");
    data.setType(null);
    data.setOperator("foo");
    data.setValues(Collections.singletonList("foo"));
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    dto.setFilter(Collections.singletonList(variableFilterDto));

    // when
    Response response = getResponse(dto);

    // then
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void validationExceptionOnNullNameField() {

    //given
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(TEST_DEFINITION);

    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName(null);
    data.setType("foo");
    data.setOperator("foo");
    data.setValues(Collections.singletonList("foo"));
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    dto.setFilter(Collections.singletonList(variableFilterDto));

    // when
    Response response = getResponse(dto);

    // then
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void validationExceptionOnNullOperatorField() {

    //given
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(TEST_DEFINITION);

    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName("foo");
    data.setType("foo");
    data.setOperator(null);
    data.setValues(Collections.singletonList("foo"));
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    dto.setFilter(Collections.singletonList(variableFilterDto));

    // when
    Response response = getResponse(dto);

    // then
    assertThat(response.getStatus(),is(500));
  }

  private Date nowDate() {
    return new Date();
  }


  private String nowDateAsString() {
    return sdf.format(nowDate());
  }

  private Date nowDateMinusSeconds(int nSeconds) {
    long nSecondsInMilliSecond = (nSeconds * 1000L);
    return new Date(System.currentTimeMillis() - nSecondsInMilliSecond);
  }

  private Date nowDatePlus10Seconds() {
    long nSecondsInMilliSecond = (10 * 1000L);
    return new Date(System.currentTimeMillis() + nSecondsInMilliSecond);
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

  private Object changeNumericValueToType(int value, String type) throws ParseException {
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

  private HeatMapQueryDto createHeatMapQueryWithVariableFilter(String processDefinitionId, VariableFilterDto variable) {
    return createHeatMapQueryWithVariableFilters(processDefinitionId, Collections.singletonList(variable));
  }

  private HeatMapQueryDto createHeatMapQueryWithVariableFilters(String processDefinitionId, List<FilterDto> variables) {
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setFilter(variables);
    return dto;
  }

  private void assertResults(HeatMapResponseDto resultMap, int size, long piCount) {
    assertThat("flow nodes count", resultMap.getFlowNodes().size(), is(size));
    assertThat("PI count", resultMap.getPiCount(), is(piCount));
  }

  private HeatMapResponseDto getHeatMapResponseDto(HeatMapQueryDto dto) {
    Response response = getResponse(dto);

    // then the status code is okay
    return response.readEntity(HeatMapResponseDto.class);
  }

  private String deploySimpleProcessDefinition() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .endEvent()
      .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    return processDefinitionId;
  }

  private Response getResponse(HeatMapQueryDto dto) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Entity<HeatMapQueryDto> entity = Entity.entity(dto, MediaType.APPLICATION_JSON);
    return embeddedOptimizeRule.target("process-definition/heatmap/frequency")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .post(entity);
  }

}
