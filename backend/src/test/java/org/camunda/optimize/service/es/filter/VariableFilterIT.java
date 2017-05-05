package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.FilterMapDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.SimpleEventDto;
import org.camunda.optimize.dto.optimize.SimpleVariableDto;
import org.camunda.optimize.dto.optimize.VariableFilterDto;
import org.camunda.optimize.dto.optimize.VariableValueDto;
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
  private final String VARIABLE_TYPE_STRING = "String";
  private final String VARIABLE_TYPE_INTEGER = "Integer";
  private final String VARIABLE_TYPE_LONG = "Long";
  private final String VARIABLE_TYPE_SHORT = "Short";
  private final String VARIABLE_TYPE_DOUBLE = "Double";
  private final String VARIABLE_TYPE_BOOLEAN = "Boolean";
  private final String VARIABLE_TYPE_DATE = "Date";
  private final String[] NUMERIC_TYPES =
    {VARIABLE_TYPE_INTEGER, VARIABLE_TYPE_SHORT, VARIABLE_TYPE_LONG, VARIABLE_TYPE_DOUBLE};


  @Test
  public void simpleVariableFilter() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_STRING, "value");
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_STRING, "anotherValue");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", VARIABLE_NAME, VARIABLE_TYPE_STRING, "value");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void variablesWithDifferentNameAreFiltered() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_STRING, "value");
    addProcessInstanceWithVariableToElasticsearch("2", "anotherVariable", VARIABLE_TYPE_STRING, "value");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", VARIABLE_NAME, VARIABLE_TYPE_STRING, "value");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void variablesWithDifferentTypeAreFiltered() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_STRING, "1");
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_INTEGER, "1");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", VARIABLE_NAME, VARIABLE_TYPE_STRING, "1");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void stringInequalityVariableFilter() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_STRING, "value");
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_STRING, "anotherValue");
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, VARIABLE_TYPE_STRING, "aThirdValue");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("!=", VARIABLE_NAME, VARIABLE_TYPE_STRING, "value");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void booleanTrueVariableFilter() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_BOOLEAN, "true");
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_BOOLEAN, "false");
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, VARIABLE_TYPE_BOOLEAN, "false");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", VARIABLE_NAME, VARIABLE_TYPE_BOOLEAN, "false");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void booleanFalseVariableFilter() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_BOOLEAN, "true");
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_BOOLEAN, "true");
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, VARIABLE_TYPE_BOOLEAN, "false");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", VARIABLE_NAME, VARIABLE_TYPE_BOOLEAN, "true");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void booleanVariableFilterWithUnsupportedOperator() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_BOOLEAN, "true");
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_BOOLEAN, "true");
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, VARIABLE_TYPE_BOOLEAN, "false");
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("!=", VARIABLE_NAME, VARIABLE_TYPE_BOOLEAN, "true");
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
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDateMinusSeconds(1));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("<", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDate());
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void dateLessThanEqualVariableFilter() throws Exception {
    // given
    String now = nowDate();
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_DATE, now);
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("<=", VARIABLE_NAME, VARIABLE_TYPE_DATE, now);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void dateGreaterThanVariableFilter() throws Exception {
    // given
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDate());
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter(">", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDateMinusSeconds(2));
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void dateGreaterThanEqualVariableFilter() throws Exception {

    // given
    String now = nowDate();
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_DATE, now);
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter(">=", VARIABLE_NAME, VARIABLE_TYPE_DATE, now);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void dateEqualVariableFilter() throws Exception {

    // given
    String now = nowDate();
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_DATE, now);
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("=", VARIABLE_NAME, VARIABLE_TYPE_DATE, now);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(TEST_DEFINITION, filter);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void dateUnequalVariableFilter() throws Exception {

    // given
    String now = nowDate();
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_DATE, now);
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("!=", VARIABLE_NAME, VARIABLE_TYPE_DATE, now);
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
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDate());
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowMinus2Seconds);
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowPlus10Seconds);
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter(">", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowMinus2Seconds);
    VariableFilterDto filter2 = createVariableFilter("<", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowPlus10Seconds);
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilters(TEST_DEFINITION, new VariableFilterDto[]{filter, filter2});
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(token, queryDto);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void dateOffRangeVariableFilter() throws Exception {

    // given
    String now = nowDate();
    addProcessInstanceWithVariableToElasticsearch("1", VARIABLE_NAME, VARIABLE_TYPE_DATE, now);
    addProcessInstanceWithVariableToElasticsearch("2", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDateMinusSeconds(2));
    addProcessInstanceWithVariableToElasticsearch("3", VARIABLE_NAME, VARIABLE_TYPE_DATE, nowDatePlusSeconds(10));
    String token = embeddedOptimizeRule.authenticateAdmin();

    // when
    VariableFilterDto filter = createVariableFilter("<", VARIABLE_NAME, VARIABLE_TYPE_DATE, now);
    VariableFilterDto filter2 = createVariableFilter(">", VARIABLE_NAME, VARIABLE_TYPE_DATE, now);
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
    filter.setValues(Collections.singletonList(variableValue));
    return filter;
  }

  private void addProcessInstanceWithVariableToElasticsearch(String id, String name, String type, String value) throws ParseException {
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);

    SimpleVariableDto variableDto = new SimpleVariableDto();
    variableDto.setName(name);
    variableDto.setType(type);
    variableDto.setValue(parseValue(type, value));
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(id);
    procInst.setVariables(Collections.singletonList(variableDto));
    procInst.setEvents(Collections.singletonList(event));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), id, procInst);
  }

  private VariableValueDto parseValue(String type, String value) throws ParseException {
    switch (type.toLowerCase()) {
      case "string":
        return new VariableValueDto(value);
      case "integer":
        return new VariableValueDto(Integer.parseInt(value));
      case "long":
        return new VariableValueDto(Long.parseLong(value));
      case "short":
        return new VariableValueDto(Short.parseShort(value));
      case "double":
        return new VariableValueDto(Double.parseDouble(value));
      case "boolean":
        return new VariableValueDto(Boolean.parseBoolean(value));
      case "date":
        return new VariableValueDto(sdf.parse(value));
    }
    return new VariableValueDto(value);
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
