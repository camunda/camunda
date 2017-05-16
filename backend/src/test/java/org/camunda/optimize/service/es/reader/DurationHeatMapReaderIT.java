package org.camunda.optimize.service.es.reader;

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
import java.util.Collections;
import java.util.Date;

import static org.camunda.optimize.service.es.reader.DurationHeatMapReader.MI_BODY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class DurationHeatMapReaderIT {

  private static final String TEST_DEFINITION = "testDefinition";
  private static final String TEST_DEFINITION_2 = "testDefinition2";
  private static final String TEST_ACTIVITY = "testActivity";
  private static final String PROCESS_INSTANCE_ID = "testProcessInstanceId";
  private static final String PROCESS_INSTANCE_ID_2 = "testProcessInstanceId2";
  private static final String TEST_ACTIVITY_2 = "testActivity_2";

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
    event.setDurationInMs(100L);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setEvents(Collections.singletonList(event));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "5", procInst);

    event.setDurationInMs(300L);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "6", procInst);

    // when
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 200L, 1L);
  }

  @Test
  public void getHeatMapForMultipleActivities() throws Exception {

    // given
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setDurationInMs(100L);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setEvents(Collections.singletonList(event));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "5", procInst);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "6", procInst);

    event.setDurationInMs(200L);
    event.setActivityId(TEST_ACTIVITY_2);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "7", procInst);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "8", procInst);

    // when
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 2, TEST_ACTIVITY, 100L, 1L);
    assertResults(testDefinition, 2, TEST_ACTIVITY_2, 200L, 1L);
  }

  @Test
  public void getHeatMapForMultipleProcessInstances() throws Exception {

    // given
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setDurationInMs(400L);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setEvents(Collections.singletonList(event));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "5", procInst);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "6", procInst);

    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID_2);
    event.setDurationInMs(200L);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "7", procInst);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "8", procInst);

    // when
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 300L, 2L);
  }

  @Test
  public void getHeatMapForMultipleProcessDefinitions() throws Exception {
    // given
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setDurationInMs(400L);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setEvents(Collections.singletonList(event));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "5", procInst);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "6", procInst);

    procInst.setProcessDefinitionId(TEST_DEFINITION_2);
    event.setDurationInMs(200L);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "7", procInst);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "8", procInst);

    // when
    HeatMapResponseDto testDefinition1 = getHeatMapResponseDto(TEST_DEFINITION);
    HeatMapResponseDto testDefinition2 = getHeatMapResponseDto(TEST_DEFINITION_2);

    // then
    assertResults(testDefinition1, 1, TEST_ACTIVITY, 400L, 1L);
    assertResults(testDefinition2, 1, TEST_ACTIVITY, 200L, 1L);
  }

  @Test
  public void getHeatMapCanHandleIrrationalAverageNumber() {
    // given
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setDurationInMs(100L);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setEvents(Collections.singletonList(event));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "5", procInst);

    event.setDurationInMs(300L);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "6", procInst);

    event.setDurationInMs(600L);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "7", procInst);

    // when
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 333L, 1L);
  }

  @Test
  public void noEventMatchesReturnEmptyResult() {
    // when
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(TEST_DEFINITION);

    // then
    assertThat(testDefinition.getPiCount(), is(0L));
    assertThat(testDefinition.getFlowNodes().size(), is(0));
  }

  @Test
  public void getHeatMapWithMIBody() throws Exception {

    // given
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setDurationInMs(100L);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setEvents(Collections.singletonList(event));

    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "5", procInst);

    event.setActivityId(TEST_ACTIVITY);
    event.setActivityType(MI_BODY);
    event.setDurationInMs(1000L);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "6", procInst);

    event.setActivityId(TEST_ACTIVITY_2);
    event.setDurationInMs(400L);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "7", procInst);

    // when
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 100L, 1L);
  }

  @Test
  public void testGetHeatMapWithLtStartDateCriteria() throws Exception {
    //given
    Date past = new Date();
    ProcessInstanceDto data = prepareESData(past);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", data);

    String operator = ">";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    String token = embeddedOptimizeRule.authenticateAdmin();
    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 0,0L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 0,0L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);
  }

  @Test
  public void testGetHeatMapWithLteStartDateCriteria() throws Exception {
    //given
    Date past = new Date();
    ProcessInstanceDto data = prepareESData(past);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", data);

    String operator = ">=";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    String token = embeddedOptimizeRule.authenticateAdmin();
    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 1, 1L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);
  }

  @Test
  public void testGetHeatMapWithLteEndDateCriteria() throws Exception {
    //given
    Date past = new Date();
    ProcessInstanceDto data = prepareESData(past);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", data);

    String operator = ">=";
    String type = "end_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    String token = embeddedOptimizeRule.authenticateAdmin();
    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);
  }

  @Test
  public void testGetHeatMapWithGtStartDateCriteria() throws Exception {
    //given
    Date past = new Date();
    ProcessInstanceDto data = prepareESData(past);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", data);

    String operator = "<";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    String token = embeddedOptimizeRule.authenticateAdmin();
    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
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

    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    operator = "<";
    type = "end_date";
    DataUtilHelper.addDateFilter(operator, type, new Date(past.getTime() + 1000), dto);

    //when
    String token = embeddedOptimizeRule.authenticateAdmin();
    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 1, 1L);

    //given
    operator = ">";
    type = "start_date";
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
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

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    String token = embeddedOptimizeRule.authenticateAdmin();
    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
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

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    String token = embeddedOptimizeRule.authenticateAdmin();
    HeatMapResponseDto resultMap = getHeatMapResponseDto(token, dto);

    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = getHeatMapResponseDto(token, dto);
    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void getHeatMapWithMoreThenTenEvents() throws Exception {
    // given
    for (int i = 0; i < 11; i++) {
      SimpleEventDto event = new SimpleEventDto();
      event.setActivityId(TEST_ACTIVITY);
      event.setDurationInMs(100L);
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
    assertResults(testDefinition, 1, TEST_ACTIVITY, 100L, 1L);

  }

  private void assertResults(HeatMapResponseDto resultMap, int activityCount, long piCount) {
    assertThat(resultMap.getFlowNodes().size(), is(activityCount));
    assertThat(resultMap.getPiCount(), is(piCount));
  }

  public void assertResults(HeatMapResponseDto resultMap, int activityCount, String activity, Long averageDuration, Long piCount) {
    this.assertResults(resultMap, activityCount, piCount);
    assertThat(resultMap.getFlowNodes().get(activity), is(averageDuration));
  }

  private ProcessInstanceDto prepareESData(Date past) {
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setDurationInMs(100L);
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(TEST_DEFINITION);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setEvents(Collections.singletonList(event));
    procInst.setStartDate(past);
    procInst.setEndDate(past);

    return procInst;
  }

  private HeatMapQueryDto createStubHeatMapQueryDto(ProcessInstanceDto data, String operator, String type, Date dateValue) {
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(data.getProcessDefinitionId());
    DataUtilHelper.addDateFilter(operator, type, dateValue, dto);
    return dto;
  }
  
  private HeatMapResponseDto getHeatMapResponseDto(String testDefinition) {
    String token = embeddedOptimizeRule.authenticateAdmin();
    Response response =
        embeddedOptimizeRule.target("process-definition/" + testDefinition + "/heatmap/duration")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();
    return response.readEntity(HeatMapResponseDto.class);
  }

  private HeatMapResponseDto getHeatMapResponseDto(String token, HeatMapQueryDto dto) {
    Response response = getResponse(token, dto);

    // then the status code is okay
    return response.readEntity(HeatMapResponseDto.class);
  }

  private Response getResponse(String token, HeatMapQueryDto dto) {
    Entity<HeatMapQueryDto> entity = Entity.entity(dto, MediaType.APPLICATION_JSON);
    return embeddedOptimizeRule.target("process-definition/heatmap/duration")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .post(entity);
  }
  
}
