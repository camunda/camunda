package org.camunda.optimize.service.es.query;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractServiceTaskBuilder;
import org.camunda.optimize.dto.optimize.query.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.HeatMapResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DataUtilHelper;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class FrequencyHeatMapQueryIT {

  private static final String TEST_DEFINITION = "testDefinition";
  private static final String TEST_ACTIVITY = "testActivity";
  private static final String TEST_ACTIVITY_2 = "testActivity_2";
  private static final long TIME_OFFSET_MILLS = 2000L;

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Before
  public void setUp() {
    embeddedOptimizeRule.resetImportStartIndexes();
  }

  @Test
  public void getHeatMap() throws Exception {

    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importEngineEntities();

    // when
    String processDefinitionId = processInstanceDto.getDefinitionId();
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(processDefinitionId);

    // then
    assertResults(testDefinition, 3, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void getHeatMapMultipleEvents() throws Exception {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    engineRule.startProcessInstance(engineDto.getDefinitionId());
    deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY_2);
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(engineDto.getDefinitionId());

    // then
    assertResults(testDefinition, 3, TEST_ACTIVITY, 2L, 2L);
  }

  @Test
  public void getHeatMapMultipleEventsWithMultipleProcesses() throws Exception {
    // given
    ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionId1 = instanceDto.getDefinitionId();
    engineRule.startProcessInstance(processDefinitionId1);

    instanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionId2 = instanceDto.getDefinitionId();
    embeddedOptimizeRule.importEngineEntities();

    // when
    HeatMapResponseDto testDefinition1 = getHeatMapResponseDto(processDefinitionId1);
    HeatMapResponseDto testDefinition2 = getHeatMapResponseDto(processDefinitionId2);

    // then
    assertResults(testDefinition1, 3, TEST_ACTIVITY, 2L, 2L);
    assertResults(testDefinition2, 3, TEST_ACTIVITY, 1L, 1L);
  }
  
  @Test
  public void getHeatMapWithMoreThenTenEvents() throws Exception {
    // given
    AbstractServiceTaskBuilder serviceTaskBuilder = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask(TEST_ACTIVITY + 0)
      .camundaExpression("${true}");
    for (int i = 1; i < 11; i++) {
      serviceTaskBuilder = serviceTaskBuilder
        .serviceTask(TEST_ACTIVITY + i)
        .camundaExpression("${true}");
    }
    BpmnModelInstance processModel =
      serviceTaskBuilder.endEvent()
        .done();

    ProcessInstanceEngineDto instanceDto = engineRule.deployAndStartProcess(processModel);
    String processDefinitionId = instanceDto.getDefinitionId();
    embeddedOptimizeRule.importEngineEntities();

    // when
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(processDefinitionId);

    // then
    assertResults(testDefinition, 13,1L);
  }

  private HeatMapResponseDto getHeatMapResponseDto(String id) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
        embeddedOptimizeRule.target("process-definition/" + id + "/heatmap/frequency")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();
    return response.readEntity(HeatMapResponseDto.class);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String activityId) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
        .startEvent()
        .serviceTask(activityId)
          .camundaExpression("${true}")
        .endEvent()
      .done();
    return engineRule.deployAndStartProcess(processModel);
  }

  @Test
  public void testGetHeatMapWithLtStartDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    Date past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getStartTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.importEngineEntities();

    // when
    String operator = ">";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 0,0L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past);
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 0,0L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);
  }

  private HeatMapResponseDto getHeatMapResponseDto(HeatMapQueryDto dto) {

    Response response = getResponse(dto);

    // then the status code is okay
    return response.readEntity(HeatMapResponseDto.class);
  }

  private Response getResponse(HeatMapQueryDto dto) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Entity<HeatMapQueryDto> entity = Entity.entity(dto, MediaType.APPLICATION_JSON);
    return embeddedOptimizeRule.target("process-definition/heatmap/frequency")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .post(entity);
  }

  @Test
  public void testGetHeatMapWithLteStartDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    Date past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getStartTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.importEngineEntities();

    String operator = ">=";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past);
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 3, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void testGetHeatMapWithLteEndDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    Date past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getEndTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.importEngineEntities();

    String operator = ">=";
    String type = "end_date";

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past);
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void testGetHeatMapWithGtStartDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    Date past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getStartTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.importEngineEntities();

    String operator = "<";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past);
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void testGetHeatMapWithMixedDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    Date past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getStartTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.importEngineEntities();

    String operator = ">";
    String type = "start_date";

    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    operator = "<";
    type = "end_date";
    DataUtilHelper.addDateFilter(operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS), dto);

    //when
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 3, 1L);

    //given
    operator = ">";
    type = "start_date";
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    operator = "<";
    type = "end_date";
    DataUtilHelper.addDateFilter(operator, type, new Date(past.getTime()), dto);

    //when
    resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void testGetHeatMapWithGtEndDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    Date past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getEndTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.importEngineEntities();

    String operator = "<";
    String type = "end_date";

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past);
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void testGetHeatMapWithGteStartDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    Date past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getStartTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.importEngineEntities();

    String operator = "<=";
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() + TIME_OFFSET_MILLS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past);
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, new Date(past.getTime() - TIME_OFFSET_MILLS));
    resultMap = getHeatMapResponseDto(dto);
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

  private HeatMapQueryDto createHeatMapQueryWithDateFilter(String processDefinitionId, String operator, String type, Date dateValue) {
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    DataUtilHelper.addDateFilter(operator, type, dateValue, dto);
    return dto;
  }

  @Test
  public void testValidationExceptionOnNullDto() {
    //when
    assertThat(getResponse(null).getStatus(),is(500));
  }

  @Test
  public void testValidationExceptionOnNullProcessDefinition() {

    //when
    assertThat(getResponse( new HeatMapQueryDto()).getStatus(),is(500));
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
    assertThat(getResponse(dto).getStatus(),is(500));
  }

}