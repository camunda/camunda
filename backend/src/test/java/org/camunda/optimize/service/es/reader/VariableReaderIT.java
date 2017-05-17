package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.variable.GetVariablesResponseDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.variable.value.BooleanVariableDto;
import org.camunda.optimize.dto.optimize.variable.value.DateVariableDto;
import org.camunda.optimize.dto.optimize.variable.value.DoubleVariableDto;
import org.camunda.optimize.dto.optimize.variable.value.IntegerVariableDto;
import org.camunda.optimize.dto.optimize.variable.value.LongVariableDto;
import org.camunda.optimize.dto.optimize.variable.value.ShortVariableDto;
import org.camunda.optimize.dto.optimize.variable.value.StringVariableDto;
import org.camunda.optimize.dto.optimize.variable.value.VariableInstanceDto;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
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

  private SimpleDateFormat simpleDateFormat;

  @Before
  public void init() {
    simpleDateFormat = new SimpleDateFormat(elasticSearchRule.getDateFormat());
  }

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void maxVariableValueListSize() {

    // given
    StringVariableDto variableDto = new StringVariableDto();
    variableDto.setName("varName");
    variableDto.setType("String");
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.addVariableInstance(variableDto);
    int exceededMaxVariableValue = embeddedOptimizeRule.getMaxVariableValueListSize() + 1;
    for (int i = 0; i < exceededMaxVariableValue; i++) {
      variableDto.setValue(String.valueOf(i));
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

    int exceededMaxVariableValue = embeddedOptimizeRule.getMaxVariableValueListSize() + 1;
    // given
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    for (int i = 0; i < exceededMaxVariableValue; i++) {
      StringVariableDto variableDto = new StringVariableDto();
      variableDto.setName("varName");
      variableDto.setType("String");
      variableDto.setValue(String.valueOf(i));
      procInst.addVariableInstance(variableDto);
    }
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "asdfasd", procInst);

    // when
    List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

    // then
    assertThat(variables.size(), is(1));
    GetVariablesResponseDto responseDto = variables.get(0);
    assertThat(responseDto.getValues().size(), is(embeddedOptimizeRule.getMaxVariableValueListSize()));
    assertThat(responseDto.isValuesAreComplete(), is(false));
  }

  @Test
  public void getVariables() {
    // given
    StringVariableDto variableDto = new StringVariableDto();
    variableDto.setName("var1");
    variableDto.setType("String");
    variableDto.setValue("value1");
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.addVariableInstance(variableDto);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", procInst);

    variableDto.setName("var2");
        variableDto.setValue("value2");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "2", procInst);

    variableDto.setName("var3");
        variableDto.setValue("value3");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "3", procInst);

    // when
    List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

    // then
    assertThat(variables.size(), is(3));
    assertVariableNameValueRelation(variables, "var1", "value1");
    assertVariableNameValueRelation(variables, "var2", "value2");
    assertVariableNameValueRelation(variables, "var3", "value3");
  }

  private void assertVariableNameValueRelation(List<GetVariablesResponseDto> variables, String name, String value) {
    boolean found = false;
    for (GetVariablesResponseDto variable : variables) {
      if (variable.getName().equals(name)) {
        assertThat(variable.getValues().get(0), is(value));
        assertThat(variable.isValuesAreComplete(), is(true));
        assertThat(variable.getType(), is("String"));
        found = true;
      }
    }
    assertThat(found, is(true));
  }

  @Test
  public void variableWithSameNameAndDifferentType() {
    // given
    StringVariableDto stringVariable = new StringVariableDto();
    stringVariable.setName("varName");
    stringVariable.setType("String");
    stringVariable.setValue("aValue");

    BooleanVariableDto booleanVariableDto = new BooleanVariableDto();
    booleanVariableDto.setName("varName");
    booleanVariableDto.setType("Boolean");
    booleanVariableDto.setValue(true);

    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.addVariableInstance(stringVariable);
    procInst.addVariableInstance(booleanVariableDto);

    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", procInst);

    // when
    List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

    // then
    assertThat(variables.size(), is(2));
    for (GetVariablesResponseDto variable : variables) {
      assertThat(variable.getName(), is("varName"));
      assertThat(variable.getValues().size(), is(1));
      assertThat(variable.isValuesAreComplete(), is(true));
    }
  }

  @Test
  public void variablesInDifferentProcessDefinitionDoesNotAffectResult() {
    // given
    StringVariableDto variableDto = new StringVariableDto();
    variableDto.setName("varName");
    variableDto.setType("String");
    variableDto.setValue("value1");
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.addVariableInstance(variableDto);
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
  public void allPrimitiveTypesCanBeRead() throws ParseException {
    // given
    Map<String, VariableInstanceDto> typeValueMap = preparePrimitiveTypeValueMap();

    for (Map.Entry<String, VariableInstanceDto> typeValueEntry : typeValueMap.entrySet()) {

      typeValueEntry.getValue().setName("varName");
      ProcessInstanceDto procInst = new ProcessInstanceDto();
      procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
      procInst.addVariableInstance(typeValueEntry.getValue());
      elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", procInst);

      // when
      List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

      // then
      assertThat(variables.size(), is(1));
      GetVariablesResponseDto responseDto = variables.get(0);
      assertThat(responseDto.getName(), is("varName"));
      assertThat(responseDto.getType(), is(typeValueEntry.getKey()));
      assertThat(responseDto.isValuesAreComplete(), is(true));
      String resultValueString = typeValueEntry.getValue().getValue().toString();
      if( typeValueEntry.getValue() instanceof DateVariableDto) {
        DateVariableDto dateVariableDto = (DateVariableDto) typeValueEntry.getValue();
        resultValueString = simpleDateFormat.format(dateVariableDto.getValue());
      }
      assertThat(
        responseDto.getValues().get(0),
          is(resultValueString)
      );
      elasticSearchRule.cleanAndVerify();
      embeddedOptimizeRule.stopOptimize();
      embeddedOptimizeRule.startOptimize();
    }
  }

  private Map<String, VariableInstanceDto> preparePrimitiveTypeValueMap() {
    Map<String, VariableInstanceDto> typeToValue = new HashMap<>();
    DateVariableDto dateVariableDto = new DateVariableDto();
    dateVariableDto.setValue(new Date());
    typeToValue.put("Date", dateVariableDto);
    BooleanVariableDto booleanVariableDto = new BooleanVariableDto();
    booleanVariableDto.setValue(true);
    typeToValue.put("Boolean", booleanVariableDto);
    ShortVariableDto shortVariableDto = new ShortVariableDto();
    shortVariableDto.setValue((short)2);
    typeToValue.put("Short", shortVariableDto);
    IntegerVariableDto integerVariableDto = new IntegerVariableDto();
    integerVariableDto.setValue(5);
    typeToValue.put("Integer", integerVariableDto);
    LongVariableDto longVariableDto = new LongVariableDto();
    longVariableDto.setValue(5L);
    typeToValue.put("Long", longVariableDto);
    DoubleVariableDto doubleVariableDto = new DoubleVariableDto();
    doubleVariableDto.setValue(5.5);
    typeToValue.put("Double", doubleVariableDto);
    StringVariableDto stringVariableDto =  new StringVariableDto();
    stringVariableDto.setValue("aString");
    typeToValue.put("String", stringVariableDto);
    return typeToValue;
  }

  @Test
  public void variablesAreSortedByThereUsedFrequency() {
    // given
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    int exceededMaxVariableValue = embeddedOptimizeRule.getMaxVariableValueListSize() + 1;
    for (int i = 0; i < exceededMaxVariableValue; i++) {
      IntegerVariableDto variableDto = new IntegerVariableDto();
      variableDto.setType("Integer");
      variableDto.setName("varName");
      variableDto.setValue(i);
      procInst.addVariableInstance(variableDto);
      elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), String.valueOf(i), procInst);
    }

    // when
    List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

    // then
    assertThat(variables.size(), is(1));
    GetVariablesResponseDto responseDto = variables.get(0);
    assertThat(responseDto.getValues().size(), is(15));
    assertThat(responseDto.isValuesAreComplete(), is(false));
    List<String> values = responseDto.getValues();
    for (int i=0; i<values.size(); i++) {
      assertThat(Integer.parseInt(values.get(i)), is(i));
    }
  }

  @Test
  public void variablesDoNotContainDuplicates() {
    // given
    StringVariableDto variableDto = new StringVariableDto();
    variableDto.setName("aStringVariableName");
    variableDto.setType("String");
    variableDto.setValue("aStringValue");
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.addVariableInstance(variableDto);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "1", procInst);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "2", procInst);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "3", procInst);

    // when
    List<GetVariablesResponseDto> variables = getGetVariablesResponseDtos(PROCESS_DEFINITION_ID);

    // then
    assertThat(variables.size(), is(1));
    GetVariablesResponseDto responseDto = variables.get(0);
    assertThat(responseDto.getValues().size(), is(1));
    assertVariableNameValueRelation(variables, "aStringVariableName", "aStringValue");
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

}
