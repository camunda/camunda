package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.util.DataUtilHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Date;

import static org.camunda.optimize.service.es.reader.DurationHeatMapReader.MI_BODY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/es-it-applicationContext.xml"})
public class DurationHeatMapReaderES_IT {

  private static final String TEST_DEFINITION = "testDefinition";
  private static final String TEST_DEFINITION_2 = "testDefinition2";
  private static final String TEST_ACTIVITY = "testActivity";
  private static final String PROCESS_INSTANCE_ID = "testProcessInstanceId";
  private static final String PROCESS_INSTANCE_ID_2 = "testProcessInstanceId2";
  private static final String TEST_ACTIVITY_2 = "testActivity_2";

  @Rule
  public final ExpectedException exception = ExpectedException.none();
  @Rule
  public ElasticSearchIntegrationTestRule rule = new ElasticSearchIntegrationTestRule();
  @Autowired
  private DurationHeatMapReader heatMapReader;
  @Autowired
  private ConfigurationService configurationService;

  @Test
  public void getHeatMap() throws Exception {

    // given
    EventDto event = new EventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setProcessDefinitionId(TEST_DEFINITION);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    event.setDurationInMs(100L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "5", event);

    event.setDurationInMs(300L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "6", event);

    // when
    HeatMapResponseDto testDefinition = heatMapReader.getHeatMap(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 200L, 1L);
  }

  @Test
  public void getHeatMapForMultipleActivities() throws Exception {

    // given
    EventDto event = new EventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setProcessDefinitionId(TEST_DEFINITION);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    event.setDurationInMs(100L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "5", event);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "6", event);

    event.setActivityId(TEST_ACTIVITY_2);
    event.setDurationInMs(200L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "7", event);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "8", event);

    // when
    HeatMapResponseDto testDefinition = heatMapReader.getHeatMap(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 2, TEST_ACTIVITY, 100L, 1L);
    assertResults(testDefinition, 2, TEST_ACTIVITY_2, 200L, 1L);
  }

  @Test
  public void getHeatMapForMultipleProcessInstances() throws Exception {

    // given
    EventDto event = new EventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setProcessDefinitionId(TEST_DEFINITION);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    event.setDurationInMs(400L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "5", event);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "6", event);

    event.setProcessInstanceId(PROCESS_INSTANCE_ID_2);
    event.setDurationInMs(200L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "7", event);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "8", event);

    // when
    HeatMapResponseDto testDefinition = heatMapReader.getHeatMap(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 300L, 2L);
  }

  @Test
  public void getHeatMapForMultipleProcessDefinitions() throws Exception {
    // given
    EventDto event = new EventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setProcessDefinitionId(TEST_DEFINITION);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    event.setDurationInMs(400L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "5", event);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "6", event);

    event.setProcessDefinitionId(TEST_DEFINITION_2);
    event.setDurationInMs(200L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "7", event);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "8", event);

    // when
    HeatMapResponseDto testDefinition1 = heatMapReader.getHeatMap(TEST_DEFINITION);
    HeatMapResponseDto testDefinition2 = heatMapReader.getHeatMap(TEST_DEFINITION_2);

    // then
    assertResults(testDefinition1, 1, TEST_ACTIVITY, 400L, 1L);
    assertResults(testDefinition2, 1, TEST_ACTIVITY, 200L, 1L);
  }

  @Test
  public void getHeatMapCanHandleIrrationalAverageNumber() {
    // given
    EventDto event = new EventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setProcessDefinitionId(TEST_DEFINITION);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    event.setDurationInMs(100L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "5", event);

    event.setDurationInMs(300L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "6", event);

    event.setDurationInMs(600L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "7", event);

    // when
    HeatMapResponseDto testDefinition = heatMapReader.getHeatMap(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 333L, 1L);
  }

  @Test
  public void getHeatMapWithActivityHavingNullDuration() {
    // given
    EventDto event = new EventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setProcessDefinitionId(TEST_DEFINITION);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    event.setDurationInMs(100L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "5", event);

    event.setDurationInMs(null);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "6", event);

    // when
    HeatMapResponseDto testDefinition = heatMapReader.getHeatMap(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 100L, 1L);
  }

  @Test
  public void noEventMatchesReturnEmptyResult() {
    // when
    HeatMapResponseDto testDefinition = heatMapReader.getHeatMap(TEST_DEFINITION);

    // then
    assertThat(testDefinition.getPiCount(), is(0L));
    assertThat(testDefinition.getFlowNodes().size(), is(0));
  }

  @Test
  public void getHeatMapWithMIBody() throws Exception {

    // given
    EventDto event = new EventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setProcessDefinitionId(TEST_DEFINITION);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    event.setDurationInMs(100L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "5", event);

    event.setActivityId(TEST_ACTIVITY);
    event.setActivityType(MI_BODY);
    event.setDurationInMs(1000L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "6", event);

    event.setActivityId(TEST_ACTIVITY_2);
    event.setDurationInMs(400L);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "7", event);

    // when
    HeatMapResponseDto testDefinition = heatMapReader.getHeatMap(TEST_DEFINITION);

    // then
    assertResults(testDefinition, 1, TEST_ACTIVITY, 100L, 1L);
  }

  @Test
  public void testGetHeatMapWithLtStartDateCriteria() throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "1", data);

    String operator = ">";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    HeatMapResponseDto resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertResults(resultMap, 0,0L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertResults(resultMap, 0,0L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);
  }

  @Test
  public void testGetHeatMapWithLteStartDateCriteria() throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "1", data);

    String operator = ">=";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    HeatMapResponseDto resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertResults(resultMap, 1, 1L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);
  }

  @Test
  public void testGetHeatMapWithLteEndDateCriteria() throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "1", data);

    String operator = ">=";
    String type = "end_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    HeatMapResponseDto resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);
  }

  @Test
  public void testGetHeatMapWithGtStartDateCriteria() throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "1", data);

    String operator = "<";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    HeatMapResponseDto resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void testGetHeatMapWithMixedDateCriteria() throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "1", data);

    String operator = ">";
    String type = "start_date";

    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    operator = "<";
    type = "end_date";
    DataUtilHelper.addDateFilter(operator, type, new Date(past.getTime() + 1000), dto);

    //when
    HeatMapResponseDto resultMap = heatMapReader.getHeatMap(dto);

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
    resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void testGetHeatMapWithGtEndDateCriteria() throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "1", data);

    String operator = "<";
    String type = "end_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    HeatMapResponseDto resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void testGetHeatMapWithGteStartDateCriteria() throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "1", data);

    String operator = "<=";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    HeatMapResponseDto resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertResults(resultMap, 1, TEST_ACTIVITY, 100L, 1L);

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void getHeatMapWithMoreThenTenEvents() throws Exception {
    // given
    for (int i = 0; i < 11; i++) {
      EventDto event = new EventDto();
      event.setActivityId(TEST_ACTIVITY);
      event.setProcessDefinitionId(TEST_DEFINITION);
      event.setProcessInstanceId(PROCESS_INSTANCE_ID);
      event.setDurationInMs(100L);
      int index = 5 + i;

      rule.addEntryToElasticsearch(configurationService.getEventType(), String.valueOf(index), event);
    }

    // when
    HeatMapResponseDto testDefinition = heatMapReader.getHeatMap(TEST_DEFINITION);

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

  private EventDto prepareESData(Date past) {
    EventDto data = new EventDto();
    data.setActivityId(TEST_ACTIVITY);
    data.setProcessDefinitionId(TEST_DEFINITION);
    data.setProcessInstanceId(PROCESS_INSTANCE_ID);
    data.setStartDate(past);
    data.setEndDate(past);
    data.setProcessInstanceStartDate(past);
    data.setProcessInstanceEndDate(past);
    data.setDurationInMs(100L);
    return data;
  }

  private HeatMapQueryDto createStubHeatMapQueryDto(EventDto data, String operator, String type, Date dateValue) {
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(data.getProcessDefinitionId());
    DataUtilHelper.addDateFilter(operator, type, dateValue, dto);
    return dto;
  }

}
