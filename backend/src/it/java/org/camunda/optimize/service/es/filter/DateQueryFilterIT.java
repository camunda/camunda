package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.DateFilterDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN_EQUALS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class DateQueryFilterIT {

  private Logger logger = LoggerFactory.getLogger(DateQueryFilterIT.class);

  private static final String TEST_DEFINITION = "testDefinition";
  private static final String TEST_ACTIVITY = "testActivity";
  private static final long TIME_OFFSET_MILLS = 2000L;

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void testGetHeatMapWithLtStartDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getStartTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    String operator = GREATER_THAN;
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 0,0L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past);
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 0,0L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
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
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getStartTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String operator = GREATER_THAN_EQUALS;
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past);
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 3, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void testGetHeatMapWithLteEndDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getEndTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String operator = GREATER_THAN_EQUALS;
    String type = "end_date";

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past);
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);
  }

  @Test
  public void testGetHeatMapWithGtStartDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getStartTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String operator = LESS_THAN;
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past);
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void testGetHeatMapWithMixedDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getStartTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String operator = GREATER_THAN;
    String type = "start_date";

    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
    operator = LESS_THAN;
    type = "end_date";
    DateUtilHelper.addDateFilter(operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS), dto);

    //when
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 3, 1L);

    //given
    operator = GREATER_THAN;
    type = "start_date";
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
    operator = LESS_THAN;
    type = "end_date";
    DateUtilHelper.addDateFilter(operator, type, past, dto);

    //when
    resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void testGetHeatMapWithGtEndDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getEndTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String operator = LESS_THAN;
    String type = "end_date";

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past);
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 0, 0L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 0, 0L);
  }

  @Test
  public void testGetHeatMapWithGteStartDateCriteria() throws Exception {
    //given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstanceDto.getId()).getStartTime();
    String processDefinitionId = processInstanceDto.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String operator = LESS_THAN_EQUALS;
    String type = "start_date";

    //when
    HeatMapQueryDto dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
    HeatMapResponseDto resultMap = getHeatMapResponseDto(dto);

    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past);
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 3, TEST_ACTIVITY, 1L, 1L);

    //when
    dto = createHeatMapQueryWithDateFilter(processDefinitionId, operator, type, past.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS));
    resultMap = getHeatMapResponseDto(dto);
    //then
    assertResults(resultMap, 0, 0L);
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
    DateUtilHelper.addDateFilter("blah", DateFilterDataDto.START_DATE, null, dto);
    assertThat(getResponse(dto).getStatus(),is(500));
  }

  private void assertResults(HeatMapResponseDto resultMap, int size, long piCount) {
    assertThat(resultMap.getFlowNodes().size(), is(size));
    assertThat(resultMap.getPiCount(), is(piCount));
  }

  public void assertResults(HeatMapResponseDto resultMap, int size, String activity, Long activityCount, Long piCount) {
    this.assertResults(resultMap, size, piCount);
    assertThat(resultMap.getFlowNodes().get(activity), is(activityCount));
  }

  private HeatMapQueryDto createHeatMapQueryWithDateFilter(String processDefinitionId, String operator, String type, OffsetDateTime dateValue) {
    logger.debug("Preparing query on [{}] with operator [{}], type [{}], date [{}]", processDefinitionId, operator, type, dateValue);
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    DateUtilHelper.addDateFilter(operator, type, dateValue, dto);
    return dto;
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
}