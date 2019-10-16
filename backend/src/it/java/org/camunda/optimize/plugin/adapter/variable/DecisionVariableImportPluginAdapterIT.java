/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin.adapter.variable;

import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.importing.InputInstanceDto;
import org.camunda.optimize.dto.optimize.importing.OutputInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.reader.ElasticsearchHelper.mapHits;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule.DEFAULT_DMN_DEFINITION_PATH;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DecisionVariableImportPluginAdapterIT {

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
  public void adaptInputs() {
    addDMNInputImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.dmn1.DoubleNumericInputValues");
    deployAndStartDecisionDefinition(new HashMap<String, Object>() {{
      put("amount", 200);
      put("invoiceCategory", "Misc");
    }});
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos();

    List<InputInstanceDto> list = decisionInstanceDtos.get(0).getInputs()
      .stream()
      .filter(i -> i.getType().equals(VariableType.DOUBLE))
      .collect(Collectors.toList());

    assertThat(list.get(0).getValue(), is("400.0"));
  }

  @Test
  public void skipInvalidAdaptedInputs() {
    addDMNInputImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.dmn2.ReturnInvalidInputs");
    engineIntegrationExtensionRule.deployAndStartDecisionDefinition();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos();

    List<InputInstanceDto> list = decisionInstanceDtos.get(0).getInputs();

    assertThat(list.isEmpty(), is(true));
  }

  @Test
  public void pluginReturnsMoreInputVariables() {
    addDMNInputImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.dmn3.ReturnMoreInputVariables");
    engineIntegrationExtensionRule.deployAndStartDecisionDefinition();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos();

    List<InputInstanceDto> list = decisionInstanceDtos.get(0).getInputs();

    assertThat(list.size(), is(4));
  }

  @Test
  public void importIsNotAffectedWithWrongPackagePath() {
    addDMNInputImportPluginBasePackagesToConfiguration("ding.dong.package.is.wrong");
    addDMNOutputImportPluginBasePackagesToConfiguration("not.a.valid.package.AwesomeOutputAdapter");
    deployAndStartDecisionDefinition(new HashMap<String, Object>() {{
      put("amount", 300);
      put("invoiceCategory", "Misc");
    }});

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos();

    List<InputInstanceDto> inputs = decisionInstanceDtos.get(0).getInputs();

    assertThat(inputs.size(), is(2));

    List<OutputInstanceDto> outputs = decisionInstanceDtos.get(0).getOutputs();

    assertThat(outputs.size(), is(2));

    List<InputInstanceDto> strings = inputs
      .stream()
      .filter(i -> i.getType().equals(VariableType.STRING))
      .collect(Collectors.toList());
    assertThat(strings.get(0).getValue() , is("Misc"));

    List<InputInstanceDto> doubles = inputs
      .stream()
      .filter(i -> i.getType().equals(VariableType.DOUBLE))
      .collect(Collectors.toList());

    assertThat(doubles.get(0).getValue(), is("300.0"));
  }

  @Test
  public void applySeveralInputAdapters() {
    addDMNInputImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.dmn1.DoubleNumericValues",
                                                       "org.camunda.optimize.testplugin.adapter.variable.dmn5.SetAllStringInputsToFoo");
    deployAndStartDecisionDefinition(new HashMap<String, Object>() {{
      put("amount", 300);
      put("invoiceCategory", "notFoo");
    }});

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos();

    List<InputInstanceDto> strings = decisionInstanceDtos.get(0).getInputs()
      .stream()
      .filter(i -> i.getType().equals(VariableType.STRING))
      .collect(Collectors.toList());
    assertThat(strings.get(0).getValue() , is("foo"));

    List<InputInstanceDto> doubles = decisionInstanceDtos.get(0).getInputs()
      .stream()
      .filter(i -> i.getType().equals(VariableType.DOUBLE))
      .collect(Collectors.toList());

    assertThat(doubles.get(0).getValue(), is("600.0"));
  }

  @Test
  public void adaptOutputs() {
    addDMNOutputImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.dmn4.AddNewOutput");
    deployAndStartDecisionDefinition(new HashMap<String, Object>() {{
      put("amount", 200);
      put("invoiceCategory", "Misc");
    }});
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos();

    List<OutputInstanceDto> list = decisionInstanceDtos.get(0).getOutputs();
    assertThat(list.size(), is(3));
  }

  @Test
  public void adaptingOutputsAndInputsWorksTogether() {
    addDMNOutputImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.dmn4.AddNewOutput");
    addDMNInputImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.dmn1.DoubleNumericInputValues");
    deployAndStartDecisionDefinition(new HashMap<String, Object>() {{
      put("amount", 200);
      put("invoiceCategory", "Misc");
    }});
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos();

    List<OutputInstanceDto> outputs = decisionInstanceDtos.get(0).getOutputs();
    assertThat(outputs.size(), is(3));

    List<InputInstanceDto> doubles = decisionInstanceDtos.get(0).getInputs()
      .stream()
      .filter(i -> i.getType().equals(VariableType.DOUBLE))
      .collect(Collectors.toList());

    assertThat(doubles.get(0).getValue(), is("400.0"));
  }

  private List<DecisionInstanceDto> getDecisionInstanceDtos() {
    SearchResponse response = elasticSearchIntegrationTestExtensionRule.getSearchResponseForAllDocumentsOfType(DECISION_INSTANCE_INDEX_NAME);
    return mapHits(
      response.getHits(),
      DecisionInstanceDto.class,
      embeddedOptimizeExtensionRule.getObjectMapper()
    );
  }

  public DecisionDefinitionEngineDto deployAndStartDecisionDefinition(HashMap<String, Object> variables) {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = engineIntegrationExtensionRule.deployDecisionDefinition(
      DEFAULT_DMN_DEFINITION_PATH
    );
    engineIntegrationExtensionRule.startDecisionInstance(
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
    embeddedOptimizeExtensionRule.reloadConfiguration();
  }

  private void addDMNOutputImportPluginBasePackagesToConfiguration(String... basePackages) {
    List<String> basePackagesList = Arrays.stream(basePackages)
      .map(s -> s.replaceFirst("\\.\\w+$", ""))
      .collect(Collectors.toList());
    configurationService.setDecisionOutputImportPluginBasePackages(basePackagesList);
    embeddedOptimizeExtensionRule.reloadConfiguration();
  }
}
