package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.DateFilterDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.dto.optimize.FilterMapDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/it-applicationContext.xml" })
public class HeatMapReaderIT {

  public static final String PI_COUNT = "piCount";

  public static final String TEST_DEFINITION = "testDefinition";
  public static final String TEST_DEFINITION_2 = "testDefinition2";
  public static final String TEST_ACTIVITY = "testActivity";
  public static final String PROCESS_INSTANCE_ID = "testProcessInstanceId";
  private static final String TEST_ACTIVITY_2 = "testActivity_2";

  @Rule
  public final ExpectedException exception = ExpectedException.none();

  @Autowired
  private HeatMapReader heatMapReader;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  @Rule
  public ElasticSearchIntegrationTestRule rule;

  @Test
  public void getHeatMap() throws Exception {

    // given
    EventDto event = new EventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setProcessDefinitionId(TEST_DEFINITION);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"5", event);

    // when
    Map<String, Long> testDefinition = heatMapReader.getHeatMap(TEST_DEFINITION);

    // then
    assertThat(testDefinition.size(),is(2));
    assertThat(testDefinition.get(TEST_ACTIVITY),is(1L));
    assertThat(testDefinition.get(PI_COUNT),is(1L));
  }

  @Test
  public void getHeatMapMultipleEvents() throws Exception {

    // given
    EventDto event = new EventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setProcessDefinitionId(TEST_DEFINITION);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"5", event);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"6", event);

    event.setActivityId(TEST_ACTIVITY_2);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"7", event);

    // when
    Map<String, Long> testDefinition = heatMapReader.getHeatMap(TEST_DEFINITION);

    // then
    assertThat(testDefinition.size(),is(3));
    assertThat(testDefinition.get(TEST_ACTIVITY),is(2L));

    assertThat(testDefinition.get(PI_COUNT),is(1L));
  }

  @Test
  public void getHeatMapMultipleEventsWithMultipleProcesses() throws Exception {

    // given
    EventDto event = new EventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setProcessDefinitionId(TEST_DEFINITION);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"5", event);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"6", event);

    event = new EventDto();
    event.setActivityId(TEST_ACTIVITY);
    event.setProcessDefinitionId(TEST_DEFINITION_2);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"7", event);

    // when
    Map<String, Long> testDefinition1 = heatMapReader.getHeatMap(TEST_DEFINITION);
    Map<String, Long> testDefinition2 = heatMapReader.getHeatMap(TEST_DEFINITION_2);

    // then
    assertThat(testDefinition1.size(),is(2));
    assertThat(testDefinition1.get(TEST_ACTIVITY),is(2L));
    assertThat(testDefinition2.size(),is(2));
    assertThat(testDefinition2.get(TEST_ACTIVITY),is(1L));
  }

  @Test
  public void testGetHeatMapWithLtStartDateCriteria () throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"1", data);

    String operator = ">";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    Map<String, Long> resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get(PI_COUNT),is(0L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get(PI_COUNT),is(0L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(2));
    assertThat(resultMap.get(TEST_ACTIVITY),is(1L));
    assertThat(resultMap.get(PI_COUNT),is(1L));
  }

  @Test
  public void testGetHeatMapWithLteStartDateCriteria () throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"1", data);

    String operator = ">=";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    Map<String, Long> resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertThat(resultMap.size(),is(1));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(2));
    assertThat(resultMap.get(TEST_ACTIVITY),is(1L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(2));
    assertThat(resultMap.get(TEST_ACTIVITY),is(1L));
  }

  @Test
  public void testGetHeatMapWithLteEndDateCriteria () throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"1", data);

    String operator = ">=";
    String type = "end_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    Map<String, Long> resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get(PI_COUNT),is(0L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(2));
    assertThat(resultMap.get(TEST_ACTIVITY),is(1L));
    assertThat(resultMap.get(PI_COUNT),is(1L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(2));
    assertThat(resultMap.get(TEST_ACTIVITY),is(1L));
    assertThat(resultMap.get(PI_COUNT),is(1L));
  }

  @Test
  public void testGetHeatMapWithGtStartDateCriteria () throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"1", data);

    String operator = "<";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    Map<String, Long> resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertThat(resultMap.size(),is(2));
    assertThat(resultMap.get(TEST_ACTIVITY),is(1L));
    assertThat(resultMap.get(PI_COUNT),is(1L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get(PI_COUNT),is(0L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get(PI_COUNT),is(0L));
  }

  @Test
  public void testGetHeatMapWithMixedDateCriteria () throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"1", data);

    String operator = ">";
    String type = "start_date";

    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    operator = "<";
    type = "end_date";
    DataUtilHelper.addDateFilter(operator, type, new Date(past.getTime() + 1000), dto);

    //when
    Map<String, Long> resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertThat(resultMap.size(),is(2));
    assertThat(resultMap.get(PI_COUNT),is(1L));

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
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get(PI_COUNT),is(0L));
  }

  @Test
  public void testGetHeatMapWithGtEndDateCriteria () throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"1", data);

    String operator = "<";
    String type = "end_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    Map<String, Long> resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertThat(resultMap.size(),is(2));
    assertThat(resultMap.get(TEST_ACTIVITY),is(1L));
    assertThat(resultMap.get(PI_COUNT),is(1L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get(PI_COUNT),is(0L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get(PI_COUNT),is(0L));
  }

  @Test
  public void testGetHeatMapWithGteStartDateCriteria () throws Exception {
    //given
    Date past = new Date();
    EventDto data = prepareESData(past);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"1", data);

    String operator = "<=";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() + 1000));
    Map<String, Long> resultMap = heatMapReader.getHeatMap(dto);

    //then
    assertThat(resultMap.size(),is(2));
    assertThat(resultMap.get(TEST_ACTIVITY),is(1L));
    assertThat(resultMap.get(PI_COUNT),is(1L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(2));
    assertThat(resultMap.get(TEST_ACTIVITY),is(1L));
    assertThat(resultMap.get(PI_COUNT),is(1L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get(PI_COUNT),is(0L));
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
    return data;
  }

  private HeatMapQueryDto createStubHeatMapQueryDto(EventDto data, String operator, String type, Date dateValue) {
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(data.getProcessDefinitionId());
    DataUtilHelper.addDateFilter(operator, type, dateValue, dto);
    return dto;
  }

  @Test
  public void testValidationExceptionOnNullDto () {
    //expect
    exception.expect(OptimizeValidationException.class);

    //when
    heatMapReader.getHeatMap((HeatMapQueryDto) null);
  }

  @Test
  public void testValidationExceptionOnNullProcessDefinition () {
    //expect
    exception.expect(OptimizeValidationException.class);

    //when
    heatMapReader.getHeatMap(new HeatMapQueryDto());
  }

  @Test
  public void testValidationExceptionOnNullFilterField () {
    //expect
    exception.expect(OptimizeValidationException.class);

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
    heatMapReader.getHeatMap(dto);
  }

}