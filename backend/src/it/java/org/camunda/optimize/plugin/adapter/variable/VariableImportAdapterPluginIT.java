/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin.adapter.variable;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomUtils;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.VariableDto;
import org.camunda.optimize.service.util.DateFormatterUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.importing.EngineConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class VariableImportAdapterPluginIT extends AbstractIT {

  private ConfigurationService configurationService;

  @BeforeEach
  public void setup() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void variableImportCanBeAdaptedByPlugin() {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util1");

    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    variables.put("var3", 1);
    variables.put("var4", 1);
    ProcessInstanceEngineDto processInstance = deploySimpleServiceTaskWithVariables(variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processInstance);

    // then only half the variables are added to Optimize
    assertThat(variablesResponseDtos).hasSize(2);
  }

  @Test
  public void variableImportCanBeAdaptedBySeveralPlugins() {
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
    ProcessInstanceEngineDto processInstance = deploySimpleServiceTaskWithVariables(variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processInstance);

    // then only half the variables are added to Optimize
    assertThat(variablesResponseDtos).hasSize(2);
  }

  @Test
  public void notExistingAdapterDoesNotStopImportProcess() {
    // given
    addVariableImportPluginBasePackagesToConfiguration("foo.bar");
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    ProcessInstanceEngineDto processInstance = deploySimpleServiceTaskWithVariables(variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processInstance);

    // then all the variables are added to Optimize
    assertThat(variablesResponseDtos).hasSize(2);
  }

  @Test
  public void adapterCanBeUsedToEnrichVariableImport() {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util3");
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    ProcessInstanceEngineDto processInstance = deploySimpleServiceTaskWithVariables(variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processInstance);

    // then extra variable is added to Optimize
    assertThat(variablesResponseDtos).hasSize(3);
  }

  @Test
  public void invalidPluginVariablesAreNotAddedToVariableImport() {
    // given a plugin that adds invalid variables
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util4");
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", 1);
    ProcessInstanceEngineDto processInstance = deploySimpleServiceTaskWithVariables(variables);

    // when
    importAllEngineEntitiesFromScratch();

    // then only the one valid variable instance is imported to Optimize
    final List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processInstance);
    assertThat(variablesResponseDtos)
      .hasSize(1)
      .extracting(ProcessVariableNameResponseDto::getName)
      .containsExactly("var");
  }

  @Test
  public void mapObjectVariableToPrimitiveOne() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util5");

    Map<String, Object> person = new HashMap<>();
    person.put("name", "Kermit");
    person.put("age", 50);
    ObjectMapper objectMapper = new ObjectMapper();
    String personAsString = objectMapper.writeValueAsString(person);

    VariableDto objectVariableDto = new VariableDto();
    objectVariableDto.setType(EngineConstants.VARIABLE_TYPE_OBJECT);
    objectVariableDto.setValue(personAsString);
    VariableDto.ValueInfo info = new VariableDto.ValueInfo();
    info.setObjectTypeName("org.camunda.foo.Person");
    info.setSerializationDataFormat(MediaType.APPLICATION_JSON);
    objectVariableDto.setValueInfo(info);
    Map<String, Object> variables = new HashMap<>();
    variables.put("person", objectVariableDto);
    ProcessInstanceEngineDto instanceDto = deploySimpleServiceTaskWithVariables(variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(instanceDto);

    // then only half the variables are added to Optimize
    assertThat(variablesResponseDtos)
      .hasSize(1)
      .extracting(ProcessVariableNameResponseDto::getName, ProcessVariableNameResponseDto::getType)
      .containsExactly(Tuple.tuple("personsName", VariableType.STRING));
  }

  @Test
  public void removeOptionalFieldsFromVariables() {
    // given plugin that clears optional fields
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util6");

    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    ProcessInstanceEngineDto processInstance = deploySimpleServiceTaskWithVariables(variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processInstance);

    // then all variables are stored in Optimize
    assertThat(variablesResponseDtos).hasSize(2);
  }

  @Test
  public void variableImportConvertsDateVariableFormats() {
    // given plugin that replaces date formats with improper date formats
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util7");
    final String dateVarName = "dateVar";
    Map<String, Object> variables = new HashMap<>();
    variables.put(dateVarName, new Date(RandomUtils.nextInt()));
    // We deploy 7 instances because the plugin tests 7 invalid date formats
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deploySimpleServiceTaskDefinition();
    IntStream.range(0, 7)
      .forEach(defIndex -> engineIntegrationExtension.startProcessInstance(
        processDefinitionEngineDto.getId(),
        variables
      ));

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = variablesClient
      .getProcessVariableNames(processDefinitionEngineDto.getKey(), processDefinitionEngineDto.getVersionAsString());
    final List<String> processVariableValues = variablesClient.getProcessVariableValues(
      processDefinitionEngineDto,
      dateVarName,
      VariableType.DATE
    );

    // then all variables are stored in Optimize
    assertThat(variablesResponseDtos).hasSize(1);
    assertThat(processVariableValues)
      .hasSize(7)
      .allSatisfy(DateFormatterUtil::isValidOptimizeDateFormat);
  }

  private List<ProcessVariableNameResponseDto> getVariables(ProcessInstanceEngineDto instanceDto) {
    return variablesClient
      .getProcessVariableNames(instanceDto.getProcessDefinitionKey(), instanceDto.getProcessDefinitionVersion());
  }

  private ProcessInstanceEngineDto deploySimpleServiceTaskWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = simpleServiceModel();
    ProcessInstanceEngineDto procInstance = engineIntegrationExtension.deployAndStartProcessWithVariables(
      processModel,
      variables
    );
    engineIntegrationExtension.waitForAllProcessesToFinish();
    return procInstance;
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskDefinition() {
    final BpmnModelInstance modelInstance = simpleServiceModel();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private BpmnModelInstance simpleServiceModel() {
    // @formatter:off
    return Bpmn.createExecutableProcess("aProcess" + System.currentTimeMillis())
      .name("aProcessName" + System.currentTimeMillis())
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    // @formatter:on
  }

  private void addVariableImportPluginBasePackagesToConfiguration(String... basePackages) {
    List<String> basePackagesList = Arrays.asList(basePackages);
    configurationService.setVariableImportPluginBasePackages(basePackagesList);
    embeddedOptimizeExtension.reloadConfiguration();
  }

}
