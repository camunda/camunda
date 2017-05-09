package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.FilterMapDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.SimpleEventDto;
import org.camunda.optimize.dto.optimize.VariableFilterDto;
import org.camunda.optimize.dto.optimize.variable.BooleanVariableDto;
import org.camunda.optimize.dto.optimize.variable.DateVariableDto;
import org.camunda.optimize.dto.optimize.variable.DoubleVariableDto;
import org.camunda.optimize.dto.optimize.variable.IntegerVariableDto;
import org.camunda.optimize.dto.optimize.variable.LongVariableDto;
import org.camunda.optimize.dto.optimize.variable.ShortVariableDto;
import org.camunda.optimize.dto.optimize.variable.StringVariableDto;
import org.camunda.optimize.dto.optimize.variable.VariableInstanceDto;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

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
public class VariableFilterIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();

  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule("classpath:rest/restEmbeddedOptimizeContext.xml");
  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  private SimpleDateFormat sdf;

  @Before
  public void init() {
    sdf = new SimpleDateFormat(elasticSearchRule.getDateFormat());
  }

  private final String TEST_DEFINITION = "testDefinition";
  private final String TEST_ACTIVITY = "testActivity";

  private final String VARIABLE_NAME = "var";
  private final String[] NUMERIC_TYPES =
    {INTEGER_TYPE, SHORT_TYPE, LONG_TYPE, DOUBLE_TYPE};


  @Test
  public void simpleVariableFilter() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, STRING_TYPE, "value");
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, STRING_TYPE, "anotherValue");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", VARIABLE_NAME, STRING_TYPE, "value");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void severalVariablesInSameProcessInstanceShouldNotAffectFilter() throws Exception {
    // given
    StringVariableDto stringVariableDto = new StringVariableDto();
    stringVariableDto.setName("stringVar");
    stringVariableDto.setType(STRING_TYPE);
    stringVariableDto.setValue("aStringValue");

    StringVariableDto anotherStringVariable = new StringVariableDto();
    anotherStringVariable.setName("anotherStringVar");
    anotherStringVariable.setType(STRING_TYPE);
    anotherStringVariable.setValue("anotherValue");

    BooleanVariableDto booleanVariableDto = new BooleanVariableDto();
    booleanVariableDto.setName("boolVar");
    booleanVariableDto.setType(BOOLEAN_TYPE);
    booleanVariableDto.setValue(true);

    addProcessInstanceWithSeveralVariablesToElasticsearch("id1", new VariableInstanceDto[]{booleanVariableDto, stringVariableDto, anotherStringVariable});

    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("!=", "stringVar", STRING_TYPE, "aStringValue");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 0, 0L);
  }

  @Test
  public void stringEqualityFilterWithVariableOfDifferentType() throws Exception {
    // given
    StringVariableDto stringVariableDto = new StringVariableDto();
    stringVariableDto.setName("stringVar");
    stringVariableDto.setType(STRING_TYPE);
    stringVariableDto.setValue("aStringValue");

    StringVariableDto anotherStringVariable = new StringVariableDto();
    anotherStringVariable.setName("anotherStringVar");
    anotherStringVariable.setType(STRING_TYPE);
    anotherStringVariable.setValue("anotherValue");

    BooleanVariableDto booleanVariableDto = new BooleanVariableDto();
    booleanVariableDto.setName("boolVar");
    booleanVariableDto.setType(BOOLEAN_TYPE);
    booleanVariableDto.setValue(true);

    addProcessInstanceWithSeveralVariablesToElasticsearch("id1",
      new VariableInstanceDto[]{booleanVariableDto, stringVariableDto, anotherStringVariable});

    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", "stringVar", STRING_TYPE, "aStringValue");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void stringInequalityFilterWithVariableOfDifferentTypeAndProcessInstance() throws Exception {
    // given
    StringVariableDto stringVariableDto = new StringVariableDto();
    stringVariableDto.setName("stringVar");
    stringVariableDto.setType(STRING_TYPE);
    stringVariableDto.setValue("aStringValue");

    StringVariableDto anotherStringVariable = new StringVariableDto();
    anotherStringVariable.setName("anotherStringVar");
    anotherStringVariable.setType(STRING_TYPE);
    anotherStringVariable.setValue("aStringValue");

    BooleanVariableDto booleanVariableDto = new BooleanVariableDto();
    booleanVariableDto.setName("boolVar");
    booleanVariableDto.setType(BOOLEAN_TYPE);
    booleanVariableDto.setValue(true);

    addProcessInstanceWithSeveralVariablesToElasticsearch("id1", new VariableInstanceDto[]{booleanVariableDto, stringVariableDto});
    addProcessInstanceWithSeveralVariablesToElasticsearch("id2", new VariableInstanceDto[]{booleanVariableDto, stringVariableDto});
    addProcessInstanceWithSeveralVariablesToElasticsearch("id3", new VariableInstanceDto[]{booleanVariableDto, stringVariableDto, anotherStringVariable});

    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("!=", "anotherStringVar", STRING_TYPE, "aStringValue");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void severalStringValueFiltersAreConcatenated() throws Exception {
    // given
    StringVariableDto stringVariableDto = new StringVariableDto();
    stringVariableDto.setName("stringVar");
    stringVariableDto.setType(STRING_TYPE);
    stringVariableDto.setValue("aStringValue");

    StringVariableDto anotherStringVariable = new StringVariableDto();
    anotherStringVariable.setName("stringVar");
    anotherStringVariable.setType(STRING_TYPE);
    anotherStringVariable.setValue("anotherValue");

    addProcessInstanceWithSeveralVariablesToElasticsearch("id1", new VariableInstanceDto[]{});
    addProcessInstanceWithSeveralVariablesToElasticsearch("id2", new VariableInstanceDto[]{stringVariableDto});
    addProcessInstanceWithSeveralVariablesToElasticsearch("id3", new VariableInstanceDto[]{anotherStringVariable});
    addProcessInstanceWithSeveralVariablesToElasticsearch("id4", new VariableInstanceDto[]{anotherStringVariable, stringVariableDto});

    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", "stringVar", STRING_TYPE, "aStringValue");
    filter.getValues().add("anotherValue");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 3L, 3L);
  }

  @Test
  public void variablesWithDifferentNameAreFiltered() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, STRING_TYPE, "value");
    addProcessInstanceWithVariableToElasticsearch("2", "anotherVariable", STRING_TYPE, "value");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", VARIABLE_NAME, STRING_TYPE, "value");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void variablesWithDifferentTypeAreFiltered() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, STRING_TYPE, "1");
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, INTEGER_TYPE, "1");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", VARIABLE_NAME, STRING_TYPE, "1");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void stringInequalityVariableFilter() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, STRING_TYPE, "value");
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, STRING_TYPE, "anotherValue");
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, STRING_TYPE, "aThirdValue");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("!=", VARIABLE_NAME, STRING_TYPE, "value");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void booleanTrueVariableFilter() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, BOOLEAN_TYPE, "true");
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, BOOLEAN_TYPE, "false");
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, BOOLEAN_TYPE, "false");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", VARIABLE_NAME, BOOLEAN_TYPE, "false");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void booleanFalseVariableFilter() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, BOOLEAN_TYPE, "true");
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, BOOLEAN_TYPE, "true");
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, BOOLEAN_TYPE, "false");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", VARIABLE_NAME, BOOLEAN_TYPE, "true");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void booleanVariableFilterWithUnsupportedOperator() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, BOOLEAN_TYPE, "true");
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, BOOLEAN_TYPE, "true");
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, BOOLEAN_TYPE, "false");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("!=", VARIABLE_NAME, BOOLEAN_TYPE, "true");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 3L, 3L);
  }

  @Test
  public void numericLessThanVariableFilter() throws Exception {

    String token = embeddedOptimizeRule.authenticateAdmin();
    for (String variableType : NUMERIC_TYPES) {
      // given
      addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, variableType, "1");
      addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, variableType, "2");
      addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, variableType, "10");

      // when
      VariableFilterDto filter = createVariableFilter("<", VARIABLE_NAME, variableType, "5");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

      // then
      assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
      elasticSearchRule.cleanAndVerify();
    }
  }

  @Test
  public void numericLessThanEqualVariableFilter() throws Exception {

    String token = embeddedOptimizeRule.authenticateAdmin();
    for (String variableType : NUMERIC_TYPES) {
      // given
      addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, variableType, "1");
      addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, variableType, "2");
      addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, variableType, "10");

      // when
      VariableFilterDto filter = createVariableFilter("<=", VARIABLE_NAME, variableType, "2");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

      // then
      assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
    }
  }

  @Test
  public void numericGreaterThanVariableFilter() throws Exception {

    String token = embeddedOptimizeRule.authenticateAdmin();
    for (String variableType : NUMERIC_TYPES) {
      // given
      addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, variableType, "1");
      addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, variableType, "2");
      addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, variableType, "10");

      // when
      VariableFilterDto filter = createVariableFilter(">", VARIABLE_NAME, variableType, "1");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

      // then
      assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
    }
  }

  @Test
  public void numericGreaterThanEqualVariableFilter() throws Exception {

    String token = embeddedOptimizeRule.authenticateAdmin();
    for (String variableType : NUMERIC_TYPES) {
      // given
      addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, variableType, "1");
      addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, variableType, "2");
      addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, variableType, "10");

      // when
      VariableFilterDto filter = createVariableFilter(">=", VARIABLE_NAME, variableType, "2");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

      // then
      assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
    }
  }

  @Test
  public void numericEqualVariableFilter() throws Exception {

    String token = embeddedOptimizeRule.authenticateAdmin();
    for (String variableType : NUMERIC_TYPES) {
      // given
      addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, variableType, "1");
      addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, variableType, "2");
      addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, variableType, "10");

      // when
      VariableFilterDto filter = createVariableFilter("=", VARIABLE_NAME, variableType, "2");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

      // then
      assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
    }
  }

  @Test
  public void numericUnequalVariableFilter() throws Exception {

    String token = embeddedOptimizeRule.authenticateAdmin();
    for (String variableType : NUMERIC_TYPES) {
      // given
      addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, variableType, "1");
      addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, variableType, "2");
      addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, variableType, "10");

      // when
      VariableFilterDto filter = createVariableFilter("!=", VARIABLE_NAME, variableType, "2");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

      // then
      assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
    }
  }

  @Test
  public void numericWithinRangeVariableFilter() throws Exception {

    String token = embeddedOptimizeRule.authenticateAdmin();
    for (String variableType : NUMERIC_TYPES) {
      // given
      addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, variableType, "1");
      addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, variableType, "2");
      addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, variableType, "10");

      // when
      VariableFilterDto filter = createVariableFilter(">", VARIABLE_NAME, variableType, "1");
      VariableFilterDto filter2 = createVariableFilter("<", VARIABLE_NAME, variableType, "10");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilters(TEST_DEFINITION, new VariableFilterDto[]{filter, filter2});
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

      // then
      assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
    }
  }

  @Test
  public void numericOffRangeVariableFilter() throws Exception {

    String token = embeddedOptimizeRule.authenticateAdmin();
    for (String variableType : NUMERIC_TYPES) {
      // given
      addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, variableType, "1");
      addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, variableType, "2");
      addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, variableType, "10");

      // when
      VariableFilterDto filter = createVariableFilter("<", VARIABLE_NAME, variableType, "2");
      VariableFilterDto filter2 = createVariableFilter(">", VARIABLE_NAME, variableType, "2");
      HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilters(TEST_DEFINITION, new VariableFilterDto[]{filter, filter2});
      HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

      // then
      assertResults(testDefinition, 0, 0L);
    }
  }

  @Test
  public void dateLessThanVariableFilter() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, DATE_TYPE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, DATE_TYPE, nowDateMinusSeconds(1));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, DATE_TYPE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("<", VARIABLE_NAME, DATE_TYPE, nowDate());
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void dateLessThanEqualVariableFilter() throws Exception {
    // given
    String now = nowDate();
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, DATE_TYPE, now);
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, DATE_TYPE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, DATE_TYPE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("<=", VARIABLE_NAME, DATE_TYPE, now);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void dateGreaterThanVariableFilter() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, DATE_TYPE, nowDate());
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, DATE_TYPE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, DATE_TYPE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter(">", VARIABLE_NAME, DATE_TYPE, nowDateMinusSeconds(2));
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void dateGreaterThanEqualVariableFilter() throws Exception {

    // given
    String now = nowDate();
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, DATE_TYPE, now);
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, DATE_TYPE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, DATE_TYPE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter(">=", VARIABLE_NAME, DATE_TYPE, now);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void dateEqualVariableFilter() throws Exception {

    // given
    String now = nowDate();
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, DATE_TYPE, now);
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, DATE_TYPE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, DATE_TYPE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", VARIABLE_NAME, DATE_TYPE, now);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void dateUnequalVariableFilter() throws Exception {

    // given
    String now = nowDate();
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, DATE_TYPE, now);
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, DATE_TYPE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, DATE_TYPE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("!=", VARIABLE_NAME, DATE_TYPE, now);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void dateWithinRangeVariableFilter() throws Exception {

    // given
    String nowMinus2Seconds = nowDateMinusSeconds(2);
    String nowPlus10Seconds = nowDatePlusSeconds(10);
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, DATE_TYPE, nowDate());
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, DATE_TYPE, nowMinus2Seconds);
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, DATE_TYPE, nowPlus10Seconds);
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter(">", VARIABLE_NAME, DATE_TYPE, nowMinus2Seconds);
    VariableFilterDto filter2 = createVariableFilter("<", VARIABLE_NAME, DATE_TYPE, nowPlus10Seconds);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilters(TEST_DEFINITION, new VariableFilterDto[]{filter, filter2});
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void dateOffRangeVariableFilter() throws Exception {

    // given
    String now = nowDate();
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, DATE_TYPE, now);
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, DATE_TYPE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, DATE_TYPE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("<", VARIABLE_NAME, DATE_TYPE, now);
    VariableFilterDto filter2 = createVariableFilter(">", VARIABLE_NAME, DATE_TYPE, now);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilters(TEST_DEFINITION, new VariableFilterDto[]{filter, filter2});
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 0, 0L);
  }

  @Test
  public void validationExceptionOnNullValueField() {

    //given
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(TEST_DEFINITION);
    FilterMapDto filter = new FilterMapDto();
    List<VariableFilterDto> variables = new ArrayList<>();
    VariableFilterDto variableFilter = new VariableFilterDto();
    variableFilter.setOperator("foo");
    variableFilter.setName("foo");
    variableFilter.setType("foo");
    variables.add(variableFilter);
    filter.setVariables(variables);
    dto.setFilter(filter);
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    Response response = getResponse(token, dto);

    // then
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void validationExceptionOnNullTypeField() {

    //given
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(TEST_DEFINITION);
    FilterMapDto filter = new FilterMapDto();
    List<VariableFilterDto> variables = new ArrayList<>();
    VariableFilterDto variableFilter = new VariableFilterDto();
    variableFilter.setOperator("foo");
    variableFilter.setName("foo");
    variableFilter.setValues(Collections.singletonList("foo"));
    variables.add(variableFilter);
    filter.setVariables(variables);
    dto.setFilter(filter);
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    Response response = getResponse(token, dto);

    // then
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void validationExceptionOnNullNameField() {

    //given
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(TEST_DEFINITION);
    FilterMapDto filter = new FilterMapDto();
    List<VariableFilterDto> variables = new ArrayList<>();
    VariableFilterDto variableFilter = new VariableFilterDto();
    variableFilter.setOperator("foo");
    variableFilter.setType("foo");
    variableFilter.setValues(Collections.singletonList("foo"));
    variables.add(variableFilter);
    filter.setVariables(variables);
    dto.setFilter(filter);
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    Response response = getResponse(token, dto);

    // then
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void validationExceptionOnNullOperatorField() {

    //given
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(TEST_DEFINITION);
    FilterMapDto filter = new FilterMapDto();
    List<VariableFilterDto> variables = new ArrayList<>();
    VariableFilterDto variableFilter = new VariableFilterDto();
    variableFilter.setName("foo");
    variableFilter.setType("foo");
    variableFilter.setValues(Collections.singletonList("foo"));
    variables.add(variableFilter);
    filter.setVariables(variables);
    dto.setFilter(filter);
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    Response response = getResponse(token, dto);

    // then
    assertThat(response.getStatus(),is(500));
  }


  private String nowDate() {
    return sdf.format(new Date());
  }

  private String nowDateMinusSeconds(int nSeconds) {
    long nSecondsInMilliSecond = (nSeconds * 1000L);
    return sdf.format(new Date(System.currentTimeMillis() - nSecondsInMilliSecond));
  }

  private String nowDatePlusSeconds(int nSeconds) {
    long nSecondsInMilliSecond = (nSeconds * 1000L);
    return sdf.format(new Date(System.currentTimeMillis() + nSecondsInMilliSecond));
  }

  private VariableFilterDto createVariableFilter(String operator, String variableName, String variableType, String variableValue) {
    VariableFilterDto filter = new VariableFilterDto();
    filter.setOperator(operator);
    filter.setName(variableName);
    filter.setType(variableType);
    List<String> values = new ArrayList<>();
    values.add(variableValue);
    filter.setValues(values);
    return filter;
  }

  private void addProcessInstanceWithSeveralVariablesToElasticsearch(String id, VariableInstanceDto[] variables) throws ParseException {
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(id);

    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);
    procInst.setEvents(Collections.singletonList(event));

    for (VariableInstanceDto variable : variables) {
      procInst.addVariableInstance(variable);
    }
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), id, procInst);
  }

  private void addProcessInstanceWithVariableToElasticsearch(String id, String name, String type, String value) throws ParseException {
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(id);

    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);
    procInst.setEvents(Collections.singletonList(event));

    parseValueAndAddToProcInstance(procInst, id, name, type, value);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), id, procInst);
  }

  private void parseValueAndAddToProcInstance(ProcessInstanceDto procInst, String id, String name, String type, String value) throws ParseException {
    switch (type.toLowerCase()) {
      case "string":
        StringVariableDto stringVariableDto = new StringVariableDto();
        stringVariableDto.setValue(value);
        stringVariableDto.setId(id);
        stringVariableDto.setName(name);
        procInst.getStringVariables().add(stringVariableDto);
        break;
      case "integer":
        IntegerVariableDto integerVariableDto = new IntegerVariableDto();
        integerVariableDto.setValue(Integer.parseInt(value));
        integerVariableDto.setId(id);
        integerVariableDto.setName(name);
        procInst.getIntegerVariables().add(integerVariableDto);
        break;
      case "long":
        LongVariableDto longVariableDto = new LongVariableDto();
        longVariableDto.setValue(Long.parseLong(value));
        longVariableDto.setId(id);
        longVariableDto.setName(name);
        procInst.getLongVariables().add(longVariableDto);
        break;
      case "short":
        ShortVariableDto shortVariableDto = new ShortVariableDto();
        shortVariableDto.setValue(Short.parseShort(value));
        shortVariableDto.setId(id);
        shortVariableDto.setName(name);
        procInst.getShortVariables().add(shortVariableDto);
        break;
      case "double":
        DoubleVariableDto doubleVariableDto = new DoubleVariableDto();
        doubleVariableDto.setValue(Double.parseDouble(value));
        doubleVariableDto.setId(id);
        doubleVariableDto.setName(name);
        procInst.getDoubleVariables().add(doubleVariableDto);
        break;
      case "boolean":
        BooleanVariableDto booleanVariableDto = new BooleanVariableDto();
        booleanVariableDto.setValue(Boolean.parseBoolean(value));
        booleanVariableDto.setId(id);
        booleanVariableDto.setName(name);
        procInst.getBooleanVariables().add(booleanVariableDto);
        break;
      case "date":
        DateVariableDto dateVariableDto = new DateVariableDto();
        dateVariableDto.setValue(sdf.parse(value));
        dateVariableDto.setId(id);
        dateVariableDto.setName(name);
        procInst.getDateVariables().add(dateVariableDto);
        break;
    }
  }

  private HeatMapQueryDto createHeatMapQueryWithVariableFilter(String processDefinitionId, VariableFilterDto variable) {
    return createHeatMapQueryWithVariableFilters(processDefinitionId, new VariableFilterDto[]{variable});
  }

  private HeatMapQueryDto createHeatMapQueryWithVariableFilters(String processDefinitionId, VariableFilterDto[] variables) {
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);

    FilterMapDto mapDto = new FilterMapDto();
    for (VariableFilterDto variable : variables) {
      mapDto.getVariables().add(variable);
    }
    dto.setFilter(mapDto);
    return dto;
  }

  private void assertResults(HeatMapResponseDto resultMap, int size, long piCount) {
    assertThat(resultMap.getFlowNodes().size(), is(size));
    assertThat(resultMap.getPiCount(), is(piCount));
  }

  private void assertResults(HeatMapResponseDto resultMap, int size, String activity, Long activityCount, Long piCount) {
    this.assertResults(resultMap, size, piCount);
    assertThat(resultMap.getFlowNodes().get(activity), is(activityCount));
  }

  private HeatMapResponseDto getHeatMapResponseDto(String token, HeatMapQueryDto dto) {
    Response response = getResponse(token, dto);

    // then the status code is okay
    return response.readEntity(HeatMapResponseDto.class);
  }

  private Response getResponse(String token, HeatMapQueryDto dto) {
    Entity<HeatMapQueryDto> entity = Entity.entity(dto, MediaType.APPLICATION_JSON);
    return embeddedOptimizeRule.target("process-definition/heatmap/frequency")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .post(entity);
  }

}
