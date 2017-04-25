package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.GetVariablesResponseDto;
import org.camunda.optimize.dto.optimize.VariableDto;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
    VariableDto variableDto = new VariableDto();
    variableDto.setName("varName");
    variableDto.setType("String");
    variableDto.setValue("aValue");
    variableDto.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    int exceededMaxVariableValue = 16;
    for (int i = 0; i < exceededMaxVariableValue; i++) {
      variableDto.setValue(String.valueOf(i));
      elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getVariableType(), String.valueOf(i), variableDto);
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
  public void allPrimitiveTypesCanBeRead() {
    // given
    Map<String, String> typeValueMap = preparePrimitiveTypeValueMap();

    for (Map.Entry<String, String> typeValueEntry : typeValueMap.entrySet()) {

      VariableDto variableDto = new VariableDto();
      variableDto.setName("varName");
      variableDto.setType(typeValueEntry.getKey());
      variableDto.setValue(typeValueEntry.getValue());
      variableDto.setProcessDefinitionId(PROCESS_DEFINITION_ID);
      elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getVariableType(), "1", variableDto);

      // when
      List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

      // then
      assertThat(variables.size(), is(1));
      GetVariablesResponseDto responseDto = variables.get(0);
      assertThat(responseDto.getName(), is("varName"));
      assertThat(responseDto.getType(), is(typeValueEntry.getKey()));
      assertThat(responseDto.isValuesAreComplete(), is(true));
      assertThat(responseDto.getValues().get(0), is(typeValueEntry.getValue()));

      elasticSearchRule.cleanAndVerify();
      embeddedOptimizeRule.stopOptimize();
      embeddedOptimizeRule.startOptimize();
    }
  }

  private Map<String, String> preparePrimitiveTypeValueMap() {
    Map<String, String> typeToValue = new HashMap<>();
    typeToValue.put("Date", "2017-04-08T11:37:29");
    typeToValue.put("Boolean", "true");
    typeToValue.put("Bytes", "Ado632dfPG=");
    typeToValue.put("Short", "2");
    typeToValue.put("Integer", "5");
    typeToValue.put("Long", "10");
    typeToValue.put("Double", "2.87");
    typeToValue.put("String", "aString");
    typeToValue.put("Null", null);
    return typeToValue;
  }


  @Test
  public void complexTypesCannotBeRead() {
    // given
    Map<String, String> typeValueMap = prepareComplexTypeValueMap();

    for (Map.Entry<String, String> typeValueEntry : typeValueMap.entrySet()) {

      VariableDto variableDto = new VariableDto();
      variableDto.setName("varName");
      variableDto.setType(typeValueEntry.getKey());
      variableDto.setValue(typeValueEntry.getValue());
      variableDto.setProcessDefinitionId(PROCESS_DEFINITION_ID);
      elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getVariableType(), "1", variableDto);

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
    return response.readEntity(new GenericType<List<GetVariablesResponseDto>> () {});
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
