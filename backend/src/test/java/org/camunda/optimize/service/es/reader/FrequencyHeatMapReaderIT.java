package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.query.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.SimpleEventDto;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.util.DataUtilHelper;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.camunda.optimize.service.es.reader.FrequencyHeatMapReader.MI_BODY;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class FrequencyHeatMapReaderIT {

  private static final String TEST_DEFINITION = "testDefinition";
  private static final String TEST_DEFINITION_2 = "testDefinition2";
  private static final String TEST_ACTIVITY = "testActivity";
  private static final String PROCESS_INSTANCE_ID = "testProcessInstanceId";
  private static final String TEST_ACTIVITY_2 = "testActivity_2";
  private static final long TIME_OFFSET_MILLS = 2000L;

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule("classpath:rest/restEmbeddedOptimizeContext.xml");

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  
  @Test
  public void getHeatMap() throws Exception {

    // given
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setEvents(Collections.singletonList(event));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "5", procInst);

    // when
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void getHeatMapMultipleEvents() throws Exception {

    // given
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setEvents(Collections.singletonList(event));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "5", procInst);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "6", procInst);

    event.setActivityId(TEST_ACTIVITY_2);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "7", procInst);

    // when
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 2, TEST_ACTIVITY, 2L, 1L);
  }

  @Test
  public void getHeatMapMultipleEventsWithMultipleProcesses() throws Exception {

    // given
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setEvents(Collections.singletonList(event));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "5", procInst);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "6", procInst);

    procInst.setProcessDefinitionId(TEST_DEFINITION_2);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "7", procInst);

    // when
    HeatMapResponseDto testDefinition1 = getHeatMapResponseDto(TEST_DEFINITION);
    HeatMapResponseDto testDefinition2 = getHeatMapResponseDto(TEST_DEFINITION_2);

    // then
    assertResults(testDefinition1, 1, TEST_ACTIVITY, 2L, 0L);
    assertResults(testDefinition2, 1, TEST_ACTIVITY, 1L, 0L);
  }
  
  @Test
  public void getHeatMapWithMIBody() throws Exception {

    // given
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setActivityType("flownode");
    SimpleEventDto event2 = new SimpleEventDto();
    event2.setActivityId(TEST_ACTIVITY);
    event2.setActivityType(MI_BODY);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    ArrayList<SimpleEventDto> events = new ArrayList<>();
    events.add(event);
    events.add(event2);
    procInst.setEvents(events);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "6", procInst);

    event.setActivityType(MI_BODY);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "7", procInst);

    // when
    HeatMapResponseDto actual = getHeatMapResponseDto(TEST_DEFINITION);

    // then
    assertResults(actual, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void getHeatMapWithMoreThenTenEvents() throws Exception {
    // given
    for (int i = 0; i < 11; i++) {
      SimpleEventDto event = new SimpleEventDto();
      event.setActivityId(TEST_ACTIVITY + i);
      ProcessInstanceDto procInst = new ProcessInstanceDto();
      procInst.setProcessDefinitionId(TEST_DEFINITION);
      procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
      procInst.setEvents(Collections.singletonList(event));

      int index = 5 + i;
      elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), String.valueOf(index), procInst);
    }

    // when
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 11,1L);
  }

  private HeatMapResponseDto getHeatMapResponseDto(String id) {
    String token = embeddedOptimizeRule.authenticateAdmin();
    Response response =
        embeddedOptimizeRule.target("process-definition/" + id + "/heatmap/frequency")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();
    return response.readEntity(HeatMapResponseDto.class);
  }

  @Test
  public void testGetHeatMapWithLtStartDateCriteria() throws Exception {
    //given
    Date past = new Date();
    ProcessInstanceDto data = prepareESData(past);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", data);

    String operator = ">";
    String type = "start_date";

    String token = embeddedOptimizeRule.authenticateAdmin();

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS));

    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 0,0L);

    //when
    dto = createHeatMapQueryWithDateFilter(data, operator, type, past);
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 0,0L);

    //when
    dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 1L, 1L);
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

  @Test
  public void testGetHeatMapWithLteStartDateCriteria() throws Exception {
    //given
    Date past = new Date();
    ProcessInstanceDto data = prepareESData(past);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", data);

    String operator = ">=";
    String type = "start_date";

    String token = embeddedOptimizeRule.authenticateAdmin();

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createHeatMapQueryWithDateFilter(data, operator, type, past);
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 1, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void testGetHeatMapWithLteEndDateCriteria() throws Exception {
    //given
    Date past = new Date();
    ProcessInstanceDto data = prepareESData(past);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", data);

    String operator = ">=";
    String type = "end_date";

    String token = embeddedOptimizeRule.authenticateAdmin();

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createHeatMapQueryWithDateFilter(data, operator, type, past);
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void testGetHeatMapWithGtStartDateCriteria() throws Exception {
    //given
    Date past = new Date();
    ProcessInstanceDto data = prepareESData(past);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", data);

    String operator = "<";
    String type = "start_date";

    String token = embeddedOptimizeRule.authenticateAdmin();

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(data, operator, type, past);
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void testGetHeatMapWithMixedDateCriteria() throws Exception {
    //given
    Date past = new Date();
    ProcessInstanceDto data = prepareESData(past);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", data);

    String operator = ">";
    String type = "start_date";

    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    operator = "<";
    type = "end_date";
    DataUtilHelper.addDateFilter(operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS), dto);

    String token = embeddedOptimizeRule.authenticateAdmin();

    //when
    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 1, 1L);

    //given
    operator = ">";
    type = "start_date";
    dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    operator = "<";
    type = "end_date";
    DataUtilHelper.addDateFilter(operator, type, new Date(past.getTime()), dto);

    //when
    resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void testGetHeatMapWithGtEndDateCriteria() throws Exception {
    //given
    Date past = new Date();
    ProcessInstanceDto data = prepareESData(past);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", data);

    String operator = "<";
    String type = "end_date";

    String token = embeddedOptimizeRule.authenticateAdmin();

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(data, operator, type, past);
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void testGetHeatMapWithGteStartDateCriteria() throws Exception {
    //given
    Date past = new Date();
    ProcessInstanceDto data = prepareESData(past);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", data);

    String operator = "<=";
    String type = "start_date";

    String token = embeddedOptimizeRule.authenticateAdmin();

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(data, operator, type, past);
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(data, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 0, 0L);
  }

  private void assertResults(HeatMapResponseDto resultMap, int size, long piCount) {
    assertThat(resultMap.getFlowNodes().size(), is(size));
    assertThat(resultMap.getPiCount(), is(piCount));
  }

  public void assertResults(HeatMapResponseDto resultMap, int size, String activity, Long activityCount, Long piCount) {
    this.assertResults(resultMap, size, piCount);
    assertThat(resultMap.getFlowNodes().get(activity), is(activityCount));
  }

  private ProcessInstanceDto prepareESData(Date past) {
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setEvents(Collections.singletonList(event));
    procInst.setStartDate(past);
    procInst.setEndDate(past);
    return procInst;
  }

  private HeatMapQueryDto createHeatMapQueryWithDateFilter(ProcessInstanceDto data, String operator, String type, Date dateValue) {
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(data.getProcessDefinitionId());
    DataUtilHelper.addDateFilter(operator, type, dateValue, dto);
    return dto;
  }

  @Test
  public void testValidationExceptionOnNullDto() {
    //when
    String token = embeddedOptimizeRule.authenticateAdmin();
    assertThat(getResponse(token, null).getStatus(),is(500));
  }

  @Test
  public void testValidationExceptionOnNullProcessDefinition() {

    //when
    String token = embeddedOptimizeRule.authenticateAdmin();
    assertThat(getResponse(token, new HeatMapQueryDto()).getStatus(),is(500));
  }

  @Test
  public void testValidationExceptionOnNullFilterField() {

    //when
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(TEST_DEFINITION);
    FilterMapDto filter = new FilterMapDto();
    List<DateFilterDto> dates = new ArrayList<>();
    DateFilterDto dateFilter = new DateFilterDto();
    dateFilter.setOperator("blah");
    dates.add(dateFilter);
    filter.setDates(dates);
    dto.setFilter(filter);
    String token = embeddedOptimizeRule.authenticateAdmin();
    assertThat(getResponse(token, dto).getStatus(),is(500));
  }

}