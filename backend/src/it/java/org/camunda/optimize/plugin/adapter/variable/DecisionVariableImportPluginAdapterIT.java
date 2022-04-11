/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin.adapter.variable;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.importing.InputInstanceDto;
import org.camunda.optimize.dto.optimize.importing.OutputInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getDecisionInstanceIndexAliasName;
import static org.camunda.optimize.util.DmnModels.createDefaultDmnModel;

public class DecisionVariableImportPluginAdapterIT extends AbstractIT {

  private ConfigurationService configurationService;

  @BeforeEach
  public void setup() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void adaptInputs() {
    addDMNInputImportPluginBasePackagesToConfiguration(
      "org.camunda.optimize.testplugin.adapter.variable.dmn1.DoubleNumericInputValues"
    );
    final DecisionDefinitionEngineDto decision = deployAndStartDecisionDefinition(new HashMap<>() {{
      put("amount", 200);
      put("invoiceCategory", "Misc");
    }});
    importAllEngineEntitiesFromScratch();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos(decision.getKey());

    List<InputInstanceDto> list = decisionInstanceDtos.get(0).getInputs()
      .stream()
      .filter(i -> i.getType().equals(VariableType.DOUBLE))
      .collect(Collectors.toList());

    assertThat(list.get(0).getValue()).isEqualTo("400.0");
  }

  @Test
  public void skipInvalidAdaptedInputs() {
    addDMNInputImportPluginBasePackagesToConfiguration(
      "org.camunda.optimize.testplugin.adapter.variable.dmn2.ReturnInvalidInputs"
    );
    final DecisionDefinitionEngineDto decision = engineIntegrationExtension.deployAndStartDecisionDefinition();
    importAllEngineEntitiesFromScratch();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos(decision.getKey());

    List<InputInstanceDto> list = decisionInstanceDtos.get(0).getInputs();

    assertThat(list).isEmpty();
  }

  @Test
  public void pluginReturnsMoreInputVariables() {
    addDMNInputImportPluginBasePackagesToConfiguration(
      "org.camunda.optimize.testplugin.adapter.variable.dmn3.ReturnMoreInputVariables"
    );
    final DecisionDefinitionEngineDto decision = engineIntegrationExtension.deployAndStartDecisionDefinition();
    importAllEngineEntitiesFromScratch();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos(decision.getKey());

    List<InputInstanceDto> list = decisionInstanceDtos.get(0).getInputs();

    assertThat(list).hasSize(4);
  }

  @Test
  public void importIsNotAffectedWithWrongPackagePath() {
    addDMNInputImportPluginBasePackagesToConfiguration("ding.dong.package.is.wrong");
    addDMNOutputImportPluginBasePackagesToConfiguration("not.a.valid.package.AwesomeOutputAdapter");
    final DecisionDefinitionEngineDto decision = deployAndStartDecisionDefinition(new HashMap<>() {{
      put("amount", 300);
      put("invoiceCategory", "Misc");
    }});

    importAllEngineEntitiesFromScratch();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos(decision.getKey());

    List<InputInstanceDto> inputs = decisionInstanceDtos.get(0).getInputs();

    assertThat(inputs).hasSize(2);

    List<OutputInstanceDto> outputs = decisionInstanceDtos.get(0).getOutputs();

    assertThat(outputs).hasSize(2);

    List<InputInstanceDto> strings = inputs
      .stream()
      .filter(i -> i.getType().equals(VariableType.STRING))
      .collect(Collectors.toList());
    assertThat(strings.get(0).getValue()).isEqualTo("Misc");

    List<InputInstanceDto> doubles = inputs
      .stream()
      .filter(i -> i.getType().equals(VariableType.DOUBLE))
      .collect(Collectors.toList());

    assertThat(doubles.get(0).getValue()).isEqualTo("300.0");
  }

  @Test
  public void applySeveralInputAdapters() {
    addDMNInputImportPluginBasePackagesToConfiguration(
      "org.camunda.optimize.testplugin.adapter.variable.dmn1.DoubleNumericValues",
      "org.camunda.optimize.testplugin.adapter.variable.dmn5.SetAllStringInputsToFoo"
    );
    final DecisionDefinitionEngineDto decision = deployAndStartDecisionDefinition(new HashMap<>() {{
      put("amount", 300);
      put("invoiceCategory", "notFoo");
    }});

    importAllEngineEntitiesFromScratch();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos(decision.getKey());

    List<InputInstanceDto> strings = decisionInstanceDtos.get(0).getInputs()
      .stream()
      .filter(i -> i.getType().equals(VariableType.STRING))
      .collect(Collectors.toList());
    assertThat(strings.get(0).getValue()).isEqualTo("foo");

    List<InputInstanceDto> doubles = decisionInstanceDtos.get(0).getInputs()
      .stream()
      .filter(i -> i.getType().equals(VariableType.DOUBLE))
      .collect(Collectors.toList());

    assertThat(doubles.get(0).getValue()).isEqualTo("600.0");
  }

  @Test
  public void adaptOutputs() {
    addDMNOutputImportPluginBasePackagesToConfiguration(
      "org.camunda.optimize.testplugin.adapter.variable.dmn4.AddNewOutput"
    );
    final DecisionDefinitionEngineDto decision = deployAndStartDecisionDefinition(new HashMap<>() {{
      put("amount", 200);
      put("invoiceCategory", "Misc");
    }});
    importAllEngineEntitiesFromScratch();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos(decision.getKey());

    List<OutputInstanceDto> list = decisionInstanceDtos.get(0).getOutputs();
    assertThat(list).hasSize(3);
  }

  @Test
  public void adaptingOutputsAndInputsWorksTogether() {
    addDMNOutputImportPluginBasePackagesToConfiguration(
      "org.camunda.optimize.testplugin.adapter.variable.dmn4.AddNewOutput"
    );
    addDMNInputImportPluginBasePackagesToConfiguration(
      "org.camunda.optimize.testplugin.adapter.variable.dmn1.DoubleNumericInputValues"
    );
    final DecisionDefinitionEngineDto decision = deployAndStartDecisionDefinition(new HashMap<>() {{
      put("amount", 200);
      put("invoiceCategory", "Misc");
    }});
    importAllEngineEntitiesFromScratch();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos(decision.getKey());

    List<OutputInstanceDto> outputs = decisionInstanceDtos.get(0).getOutputs();
    assertThat(outputs).hasSize(3);

    List<InputInstanceDto> doubles = decisionInstanceDtos.get(0).getInputs()
      .stream()
      .filter(i -> i.getType().equals(VariableType.DOUBLE))
      .collect(Collectors.toList());

    assertThat(doubles.get(0).getValue()).isEqualTo("400.0");
  }

  private List<DecisionInstanceDto> getDecisionInstanceDtos(final String decisionDefinitionKey) {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      getDecisionInstanceIndexAliasName(decisionDefinitionKey),
      DecisionInstanceDto.class
    );
  }

  public DecisionDefinitionEngineDto deployAndStartDecisionDefinition(HashMap<String, Object> variables) {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = engineIntegrationExtension.deployDecisionDefinition(
      createDefaultDmnModel()
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionEngineDto.getId(),
      variables
    );
    return decisionDefinitionEngineDto;
  }

  private void addDMNInputImportPluginBasePackagesToConfiguration(String... basePackages) {
    List<String> basePackagesList = Arrays.stream(basePackages)
      .map(s -> s.replaceFirst("\\.\\w+$", ""))
      .collect(Collectors.toList());
    configurationService.setDecisionInputImportPluginBasePackages(basePackagesList);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  private void addDMNOutputImportPluginBasePackagesToConfiguration(String... basePackages) {
    List<String> basePackagesList = Arrays.stream(basePackages)
      .map(s -> s.replaceFirst("\\.\\w+$", ""))
      .collect(Collectors.toList());
    configurationService.setDecisionOutputImportPluginBasePackages(basePackagesList);
    embeddedOptimizeExtension.reloadConfiguration();
  }
}
