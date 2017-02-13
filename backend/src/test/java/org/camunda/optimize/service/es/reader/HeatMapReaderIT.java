package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.DateDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.dto.optimize.FilterDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.service.es.reader.HeatMapReader;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.junit.Rule;
import org.junit.Test;
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
    event.setActivityId("testActivity");
    event.setProcessDefinitionId("testDefinitionId");
    rule.addEntryToElasticsearch(configurationService.getEventType(),"5", event);

    // when
    Map<String, Long> testDefinition = heatMapReader.getHeatMap("testDefinitionId");

    // then
    assertThat(testDefinition.size(),is(1));
    assertThat(testDefinition.get("testActivity"),is(1L));
  }

  @Test
  public void getHeatMapMultipleEvents() throws Exception {

    // given
    EventDto event = new EventDto();
    event.setActivityId("testActivity");
    event.setProcessDefinitionId("testDefinitionId");
    rule.addEntryToElasticsearch(configurationService.getEventType(),"5", event);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"6", event);

    // when
    Map<String, Long> testDefinition = heatMapReader.getHeatMap("testDefinitionId");

    // then
    assertThat(testDefinition.size(),is(1));
    assertThat(testDefinition.get("testActivity"),is(2L));
  }

  @Test
  public void getHeatMapMultipleEventsWithMultipleProcesses() throws Exception {

    // given
    EventDto event = new EventDto();
    event.setActivityId("testActivity");
    event.setProcessDefinitionId("testDefinitionId1");
    rule.addEntryToElasticsearch(configurationService.getEventType(),"5", event);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"6", event);

    event = new EventDto();
    event.setActivityId("testActivity");
    event.setProcessDefinitionId("testDefinitionId2");
    rule.addEntryToElasticsearch(configurationService.getEventType(),"7", event);

    // when
    Map<String, Long> testDefinition1 = heatMapReader.getHeatMap("testDefinitionId1");
    Map<String, Long> testDefinition2 = heatMapReader.getHeatMap("testDefinitionId2");

    // then
    assertThat(testDefinition1.size(),is(1));
    assertThat(testDefinition1.get("testActivity"),is(2L));
    assertThat(testDefinition2.size(),is(1));
    assertThat(testDefinition2.get("testActivity"),is(1L));
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
    assertThat(resultMap.size(),is(0));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(0));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get("testActivity"),is(1L));
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
    assertThat(resultMap.size(),is(0));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get("testActivity"),is(1L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get("testActivity"),is(1L));
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
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get("testActivity"),is(1L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(0));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(0));
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
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get("testActivity"),is(1L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, past);
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(1));
    assertThat(resultMap.get("testActivity"),is(1L));

    //when
    dto = createStubHeatMapQueryDto(data, operator, type, new Date(past.getTime() - 1000));
    resultMap = heatMapReader.getHeatMap(dto);
    //then
    assertThat(resultMap.size(),is(0));
  }

  private EventDto prepareESData(Date past) {
    EventDto data = new EventDto();
    data.setActivityId("testActivity");
    data.setProcessDefinitionId("testDefinition");
    past.setTime(past.getTime());
    data.setStartDate(past);
    return data;
  }

  private HeatMapQueryDto createStubHeatMapQueryDto(EventDto data, String operator, String type, Date dateValue) {
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(data.getProcessDefinitionId());
    FilterDto filter = new FilterDto();
    List<DateDto> dates = new ArrayList<>();
    DateDto date = new DateDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(dateValue);
    dates.add(date);
    filter.setDates(dates);
    dto.setFilter(filter);
    return dto;
  }

}