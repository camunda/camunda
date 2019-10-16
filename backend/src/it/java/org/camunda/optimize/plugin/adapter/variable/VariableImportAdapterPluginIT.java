/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin.adapter.variable;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class VariableImportAdapterPluginIT {

  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();
  private ConfigurationService configurationService;

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchIntegrationTestExtensionRule).around(engineIntegrationExtensionRule).around(embeddedOptimizeExtensionRule);


  @Before
  public void setup() {
    configurationService = embeddedOptimizeExtensionRule.getConfigurationService();
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void variableImportCanBeAdaptedByPlugin() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util1");

    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    variables.put("var3", 1);
    variables.put("var4", 1);
    ProcessInstanceEngineDto processDefinition = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processDefinition);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(2));
  }

  @Test
  public void variableImportCanBeAdaptedBySeveralPlugins() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration(
      "org.camunda.optimize.testplugin.adapter.variable.util1",
      "org.camunda.optimize.testplugin.adapter.variable.util2"
    );
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "bar");
    variables.put("var2", "bar");
    variables.put("var3", "bar");
    variables.put("var4", "bar");
    ProcessInstanceEngineDto processDefinition = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processDefinition);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(2));
  }

  @Test
  public void notExistingAdapterDoesNotStopImportProcess() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("foo.bar");
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    ProcessInstanceEngineDto processDefinition = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processDefinition);

    //then all the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(2));
  }

  @Test
  public void adapterCanBeUsedToEnrichVariableImport() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util3");
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    ProcessInstanceEngineDto processDefinition = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processDefinition);

    //then extra variable is added to Optimize
    assertThat(variablesResponseDtos.size(), is(3));
  }

  @Test
  public void invalidPluginVariablesAreNotAddedToVariableImport() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util4");
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", 1);
    ProcessInstanceEngineDto processDefinition = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processDefinition);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(1));
    assertThat(variablesResponseDtos.get(0).getName(), is("var"));
  }

  @Test
  public void mapComplexVariableToPrimitiveOne() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util5");

    Map<String, Object> person = new HashMap<>();
    person.put("name", "Kermit");
    person.put("age", 50);
    ObjectMapper objectMapper = new ObjectMapper();
    String personAsString = objectMapper.writeValueAsString(person);

    ComplexVariableDto complexVariableDto = new ComplexVariableDto();
    complexVariableDto.setType("Object");
    complexVariableDto.setValue(personAsString);
    ComplexVariableDto.ValueInfo info = new ComplexVariableDto.ValueInfo();
    info.setObjectTypeName("org.camunda.foo.Person");
    info.setSerializationDataFormat("application/json");
    complexVariableDto.setValueInfo(info);
    Map<String, Object> variables = new HashMap<>();
    variables.put("person", complexVariableDto);
    ProcessInstanceEngineDto instanceDto = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(instanceDto);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos.size(), is(1));
    assertThat(variablesResponseDtos.get(0).getName(), is("personsName"));
    assertThat(variablesResponseDtos.get(0).getType(), is(VariableType.STRING));
  }

  private List<ProcessVariableNameResponseDto> getVariables(ProcessInstanceEngineDto processDefinition) {
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey(processDefinition.getProcessDefinitionKey());
    variableRequestDto.setProcessDefinitionVersion(processDefinition.getProcessDefinitionVersion());
    return embeddedOptimizeExtensionRule
            .getRequestExecutor()
            .buildProcessVariableNamesRequest(variableRequestDto)
            .executeAndReturnList(ProcessVariableNameResponseDto.class, 200);
  }

  private ProcessInstanceEngineDto deploySimpleServiceTaskWithVariables(Map<String, Object> variables) throws
                                                                                                       Exception {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess" + System.currentTimeMillis())
      .name("aProcessName" + System.currentTimeMillis())
        .startEvent()
        .serviceTask()
          .camundaExpression("${true}")
        .endEvent()
      .done();
    ProcessInstanceEngineDto procInstance = engineIntegrationExtensionRule.deployAndStartProcessWithVariables(processModel, variables);
    engineIntegrationExtensionRule.waitForAllProcessesToFinish();
    return procInstance;
  }

  private void addVariableImportPluginBasePackagesToConfiguration(String... basePackages) {
    List<String> basePackagesList = Arrays.asList(basePackages);
    configurationService.setVariableImportPluginBasePackages(basePackagesList);
    embeddedOptimizeExtensionRule.reloadConfiguration();
  }

}
