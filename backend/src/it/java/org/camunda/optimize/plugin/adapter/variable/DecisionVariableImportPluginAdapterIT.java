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
import org.camunda.optimize.plugin.DecisionInputImportAdapterProvider;
import org.camunda.optimize.plugin.DecisionOutputImportAdapterProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.reader.ElasticsearchHelper.mapHits;
import static org.camunda.optimize.test.it.rule.EngineIntegrationRule.DEFAULT_DMN_DEFINITION_PATH;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DecisionVariableImportPluginAdapterIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private ConfigurationService configurationService;
  private DecisionInputImportAdapterProvider inputPluginProvider;
  private DecisionOutputImportAdapterProvider outputPluginProvider;

  @Before
  public void setup() {
    configurationService = embeddedOptimizeRule.getConfigurationService();
    inputPluginProvider = embeddedOptimizeRule.getApplicationContext().getBean(DecisionInputImportAdapterProvider.class);
    outputPluginProvider = embeddedOptimizeRule.getApplicationContext().getBean(DecisionOutputImportAdapterProvider.class);
  }

  @After
  public void resetBasePackage() {
    configurationService.setDecisionInputImportPluginBasePackages(new ArrayList<>());
    configurationService.setDecisionOutputImportPluginBasePackages(new ArrayList<>());
    inputPluginProvider.resetPlugins();
    outputPluginProvider.resetPlugins();
  }

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void adaptInputs() {
    addDMNInputImportPluginBasePackagesToConfiguration("org.camunda.optimize.plugin.adapter.variable.dmn1.DoubleNumericInputValues");
    deployAndStartDecisionDefinition(new HashMap<String, Object>() {{
      put("amount", 200);
      put("invoiceCategory", "Misc");
    }});
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos();

    List<InputInstanceDto> list = decisionInstanceDtos.get(0).getInputs()
      .stream()
      .filter(i -> i.getType().equals(VariableType.DOUBLE))
      .collect(Collectors.toList());

    assertThat(list.get(0).getValue(), is("400.0"));
  }

  @Test
  public void skipInvalidAdaptedInputs() {
    addDMNInputImportPluginBasePackagesToConfiguration("org.camunda.optimize.plugin.adapter.variable.dmn2.ReturnInvalidInputs");
    engineRule.deployAndStartDecisionDefinition();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos();

    List<InputInstanceDto> list = decisionInstanceDtos.get(0).getInputs();

    assertThat(list.isEmpty(), is(true));
  }

  @Test
  public void pluginReturnsMoreInputVariables() {
    addDMNInputImportPluginBasePackagesToConfiguration("org.camunda.optimize.plugin.adapter.variable.dmn3.ReturnMoreInputVariables");
    engineRule.deployAndStartDecisionDefinition();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
    addDMNInputImportPluginBasePackagesToConfiguration("org.camunda.optimize.plugin.adapter.variable.dmn1.DoubleNumericValues",
                                                       "org.camunda.optimize.plugin.adapter.variable.dmn5.SetAllStringInputsToFoo");
    deployAndStartDecisionDefinition(new HashMap<String, Object>() {{
      put("amount", 300);
      put("invoiceCategory", "notFoo");
    }});

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
    addDMNOutputImportPluginBasePackagesToConfiguration("org.camunda.optimize.plugin.adapter.variable.dmn4.AddNewOutput");
    deployAndStartDecisionDefinition(new HashMap<String, Object>() {{
      put("amount", 200);
      put("invoiceCategory", "Misc");
    }});
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    List<DecisionInstanceDto> decisionInstanceDtos = getDecisionInstanceDtos();

    List<OutputInstanceDto> list = decisionInstanceDtos.get(0).getOutputs();
    assertThat(list.size(), is(3));
  }

  @Test
  public void adaptingOutputsAndInputsWorksTogether() {
    addDMNOutputImportPluginBasePackagesToConfiguration("org.camunda.optimize.plugin.adapter.variable.dmn4.AddNewOutput");
    addDMNInputImportPluginBasePackagesToConfiguration("org.camunda.optimize.plugin.adapter.variable.dmn1.DoubleNumericInputValues");
    deployAndStartDecisionDefinition(new HashMap<String, Object>() {{
      put("amount", 200);
      put("invoiceCategory", "Misc");
    }});
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
    SearchResponse response = elasticSearchRule.getSearchResponseForAllDocumentsOfType(DECISION_INSTANCE_TYPE);
    return mapHits(
      response.getHits(),
      DecisionInstanceDto.class,
      embeddedOptimizeRule.getObjectMapper()
    );
  }

  public DecisionDefinitionEngineDto deployAndStartDecisionDefinition(HashMap<String, Object> variables) {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = engineRule.deployDecisionDefinition(
      DEFAULT_DMN_DEFINITION_PATH
    );
    engineRule.startDecisionInstance(
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
  }

  private void addDMNOutputImportPluginBasePackagesToConfiguration(String... basePackages) {
    List<String> basePackagesList = Arrays.stream(basePackages)
      .map(s -> s.replaceFirst("\\.\\w+$", ""))
      .collect(Collectors.toList());
    configurationService.setDecisionOutputImportPluginBasePackages(basePackagesList);
  }
}
