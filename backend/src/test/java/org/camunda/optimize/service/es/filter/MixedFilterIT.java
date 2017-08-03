package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.variable.VariableFilterDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.util.VariableHelper.STRING_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class MixedFilterIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private final static String USER_TASK_ACTIVITY_ID = "userTask";

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void applyAllPossibleFilters() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");

      // this is the process instance that should be filtered
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, variables);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());

      // wrong not executed flow node
    engineRule.startProcessInstance(processDefinitionId, variables);

    // wrong variable
    variables.put("var", "anotherValue");
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, variables);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());

    // wrong date
    Thread.sleep(1000L);
    variables.put("var", "value");
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, variables);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    Date date = engineRule.getHistoricProcessInstance(instanceEngineDto.getId()).getStartTime();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    VariableFilterDto filter = createVariableFilter("=", "var", STRING_TYPE, "value");
    HeatMapQueryDto queryDto = createHeatMapQueryWithVariableFilter(processDefinitionId, filter);
    DataUtilHelper.addDateFilter("<", "start_date", date, queryDto);
    queryDto.getFilter().getExecutedFlowNodeIds().add(USER_TASK_ACTIVITY_ID);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertThat(testDefinition.getPiCount(), is(1L));
  }

  private Response getResponse(HeatMapQueryDto dto) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Entity<HeatMapQueryDto> entity = Entity.entity(dto, MediaType.APPLICATION_JSON);
    return embeddedOptimizeRule.target("process-definition/heatmap/frequency")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .post(entity);
  }

  private HeatMapResponseDto getHeatMapResponseDto(HeatMapQueryDto dto) {
    Response response = getResponse(dto);

    // then the status code is okay
    return response.readEntity(HeatMapResponseDto.class);
  }

  private VariableFilterDto createVariableFilter(String operator, String variableName, String variableType, String variableValue) {
    VariableFilterDto filter = new VariableFilterDto();
    filter.setName(variableName);
    filter.setOperator(operator);
    filter.setType(variableType);
    List<String> values = new ArrayList<>();
    values.add(variableValue);
    filter.setValues(values);
    return filter;
  }

  private HeatMapQueryDto createHeatMapQueryWithVariableFilter(String processDefinitionId, VariableFilterDto variable) {
    return createHeatMapQueryWithVariableFilters(processDefinitionId, new VariableFilterDto[]{variable});
  }

  private HeatMapQueryDto createHeatMapQueryWithVariableFilters(String processDefinitionId, VariableFilterDto[] variables) {
    HeatMapQueryDto dto = new HeatMapQueryDto();

    FilterMapDto mapDto = new FilterMapDto();
    for (VariableFilterDto variable : variables) {
      mapDto.getVariables().add(variable);
    }
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setFilter(mapDto);
    return dto;
  }

  private String deploySimpleProcessDefinition() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .userTask(USER_TASK_ACTIVITY_ID)
      .endEvent()
      .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    return processDefinitionId;
  }


}
