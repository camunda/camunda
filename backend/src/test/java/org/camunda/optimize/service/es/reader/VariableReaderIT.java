package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.GetVariablesResponseDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.SimpleVariableDto;
import org.camunda.optimize.dto.optimize.VariableDto;
import org.camunda.optimize.dto.optimize.VariableValueDto;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class VariableReaderIT {
  
  private final String PROCESS_DEFINITION_ID = "aProcDefId";

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule("classpath:rest/restEmbeddedOptimizeContext.xml");

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void maxVariableValueListSize() {

    // given
    SimpleVariableDto variableDto = new SimpleVariableDto();
    variableDto.setName("varName");
    variableDto.setType("String");
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.setVariables(Collections.singletonList(variableDto));
    int exceededMaxVariableValue = embeddedOptimizeRule.getMaxVariableValueListSize() + 1;
    for (int i = 0; i < exceededMaxVariableValue; i++) {
      variableDto.setValue(new VariableValueDto(String.valueOf(i)));
      elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), String.valueOf(i), procInst);
    }

    // when
    List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

    // then
    assertThat(variables.size(), is(1));
    GetVariablesResponseDto responseDto = variables.get(0);
    assertThat(responseDto.getValues().size(), is(15));
    assertThat(responseDto.isValuesAreComplete(), is(false));
  }

  @Test
  public void maxVariableValueListSizeInSameProcessInstance() {

    int exceededMaxVariableValue = 16;
    // given
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.setVariables(new ArrayList<>());
    for (int i = 0; i < exceededMaxVariableValue; i++) {
      SimpleVariableDto variableDto = new SimpleVariableDto();
      variableDto.setName("varName");
      variableDto.setType("String");
      variableDto.setValue(new VariableValueDto(String.valueOf(i)));
      procInst.getVariables().add(variableDto);
    }
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "asdfasd", procInst);

    // when
    List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

    // then
    assertThat(variables.size(), is(1));
    GetVariablesResponseDto responseDto = variables.get(0);
    assertThat(responseDto.getValues().size(), is(15));
    assertThat(responseDto.isValuesAreComplete(), is(false));
  }

  @Test
  public void getVariables() {

    // given
    SimpleVariableDto variableDto = new SimpleVariableDto();
    variableDto.setName("var1");
    variableDto.setType("String");
    variableDto.setValue(new VariableValueDto("value1"));
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.setVariables(Collections.singletonList(variableDto));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", procInst);

    variableDto.setName("var2");
        variableDto.setValue(new VariableValueDto("value2"));

    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "2", procInst);

    variableDto.setName("var3");
        variableDto.setValue(new VariableValueDto("value3"));

    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "3", procInst);


    // when
    List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

    // then
    assertThat(variables.size(), is(3));
    GetVariablesResponseDto responseDto = variables.get(0);
    assertThat(responseDto.getValues().size(), is(1));
    switch (responseDto.getName()) {
      case "var1":
        assertThat(responseDto.getValues().get(0), is("value1"));
        assertThat(responseDto.isValuesAreComplete(), is(true));
        assertThat(responseDto.getType(), is("String"));
        break;
      case "var2":
        assertThat(responseDto.getValues().get(0), is("value2"));
        assertThat(responseDto.isValuesAreComplete(), is(true));
        assertThat(responseDto.getType(), is("String"));
        break;
      case "var3":
        assertThat(responseDto.getValues().get(0), is("value3"));
        assertThat(responseDto.isValuesAreComplete(), is(true));
        assertThat(responseDto.getType(), is("String"));
        break;
      default:
        fail("Should not have a different value!");
        break;
    }

  }

  @Test
  public void variableWithSameNameChangeType() {
    // given
    SimpleVariableDto variableDto = new SimpleVariableDto();
    variableDto.setName("varName");
    variableDto.setType("String");
    variableDto.setValue(new VariableValueDto("aValue"));
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);

    SimpleVariableDto variableDto2 = new SimpleVariableDto();
    variableDto2.setName("varName");
    variableDto2.setType("Boolean");
    variableDto2.setValue(new VariableValueDto(true));

    List<SimpleVariableDto> valueList = new LinkedList<>();
    valueList.add(variableDto);
    valueList.add(variableDto2);

    procInst.setVariables(valueList);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", procInst);

    // when
    List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

    // then
    assertThat(variables.size(), is(1));
    GetVariablesResponseDto responseDto = variables.get(0);
    assertThat(responseDto.getValues().size(), is(2));
    assertThat(responseDto.isValuesAreComplete(), is(true));
  }

  @Test
  public void variablesInDifferentProcessDefinitionDoesNotAffectResult() {
    // given
    SimpleVariableDto variableDto = new SimpleVariableDto();
    variableDto.setName("var1");
    variableDto.setType("String");
    variableDto.setValue(new VariableValueDto("value1"));
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.setVariables(Collections.singletonList(variableDto));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", procInst);

    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID + "2");
        elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "2", procInst);

    // when
    List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

    // then
    assertThat(variables.size(), is(1));
    GetVariablesResponseDto responseDto = variables.get(0);
    assertThat(responseDto.getValues().size(), is(1));
    assertThat(responseDto.isValuesAreComplete(), is(true));

  }


  @Test
  public void allPrimitiveTypesCanBeRead() {
    // given
    Map<String, VariableValueDto> typeValueMap = preparePrimitiveTypeValueMap();

    for (Map.Entry<String, VariableValueDto> typeValueEntry : typeValueMap.entrySet()) {

      SimpleVariableDto variableDto = new SimpleVariableDto();
      variableDto.setName("varName");
      variableDto.setType(typeValueEntry.getKey());
      variableDto.setValue(typeValueEntry.getValue());
      ProcessInstanceDto procInst = new ProcessInstanceDto();
      procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
      procInst.setVariables(Collections.singletonList(variableDto));
      elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", procInst);

      // when
      List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

      // then
      assertThat(variables.size(), is(1));
      GetVariablesResponseDto responseDto = variables.get(0);
      assertThat(responseDto.getName(), is("varName"));
      assertThat(responseDto.getType(), is(typeValueEntry.getKey()));
      assertThat(responseDto.isValuesAreComplete(), is(true));
      assertThat(
        responseDto.getValues().get(0),
          is(typeValueEntry.getValue().toString(elasticSearchRule.getDateFormat())));

      elasticSearchRule.cleanAndVerify();
      embeddedOptimizeRule.stopOptimize();
      embeddedOptimizeRule.startOptimize();
    }
  }

  private Map<String, VariableValueDto> preparePrimitiveTypeValueMap() {
    Map<String, VariableValueDto> typeToValue = new HashMap<>();
    typeToValue.put("Date", new VariableValueDto(new Date()));
    typeToValue.put("Boolean", new VariableValueDto(true));
    typeToValue.put("Short", new VariableValueDto((short)2));
    typeToValue.put("Integer", new VariableValueDto(5));
    typeToValue.put("Long", new VariableValueDto(10L));
    typeToValue.put("Double", new VariableValueDto(2.87));
    typeToValue.put("String", new VariableValueDto("aString"));
    return typeToValue;
  }


  @Test
  public void complexTypesCannotBeRead() {
    // given
    Map<String, String> typeValueMap = prepareComplexTypeValueMap();

    for (Map.Entry<String, String> typeValueEntry : typeValueMap.entrySet()) {

      SimpleVariableDto variableDto = new SimpleVariableDto();
      variableDto.setName("varName");
      variableDto.setType(typeValueEntry.getKey());
      variableDto.setValue(new VariableValueDto(typeValueEntry.getValue()));
      ProcessInstanceDto procInst = new ProcessInstanceDto();
      procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
      procInst.setVariables(Collections.singletonList(variableDto));
      elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", procInst);

      // when
      List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

      // then
      assertThat(variables.size(), is(0));
      elasticSearchRule.cleanAndVerify();
      embeddedOptimizeRule.stopOptimize();
      embeddedOptimizeRule.startOptimize();
    }
  }

  private List<GetVariablesResponseDto> getGetVariablesResponseDtos(String processDefinition) {
    String token = embeddedOptimizeRule.authenticateAdmin();
    Response response =
        embeddedOptimizeRule.target("process-definition/" + processDefinition + "/variables")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();
    return response.readEntity(new GenericType<List<GetVariablesResponseDto>>() {});
  }

  private Map<String, String> prepareComplexTypeValueMap() {
    Map<String, String> typeToValue = new HashMap<>();
    typeToValue.put("Object", "sdsfasd");
    typeToValue.put("File", null);
    typeToValue.put("Json", "asdf");
    typeToValue.put("Xml", "asdf");
    return typeToValue;
  }
}
