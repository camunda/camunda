/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.camunda.optimize.test.util.decision.DmnModelGenerator;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.of;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static java.util.Collections.nCopies;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.test.util.decision.DecisionTypeRef.BOOLEAN;
import static org.camunda.optimize.test.util.decision.DecisionTypeRef.STRING;
import static org.camunda.optimize.test.util.decision.DecisionTypeRef.values;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public abstract class DecisionVariableNameRetrievalIT extends AbstractDecisionDefinitionIT {

  protected static final String DECISION_KEY = "aDecision";

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Test
  public void getVariableNames() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = deployDecisionsWithStringVarNames(of("var1", "var2"));

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<DecisionVariableNameDto> variableResponse = getVariableNames(decisionDefinitionDto);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.get(0).getName(), is("var1"));
    assertThat(variableResponse.get(0).getId(), notNullValue());
    assertThat(variableResponse.get(0).getType(), is(VariableType.STRING));
    assertThat(variableResponse.get(1).getName(), is("var2"));
    assertThat(variableResponse.get(1).getId(), notNullValue());
    assertThat(variableResponse.get(1).getType(), is(VariableType.STRING));
  }

  @Test
  public void getVariableNamesForDefinitionWithMultipleTables() {
    // given
    // @formatter:off
    DmnModelInstance modelInstance = DmnModelGenerator
      .create()
        .decision()
          .decisionDefinitionKey("decision1")
          .addInput("inputForDecision1", STRING)
          .addOutput("outputForDecision1", STRING)
        .buildDecision()
        .decision()
          .decisionDefinitionKey("decision2")
          .addInput("inputForDecision2", STRING)
          .addOutput("outputForDecision2", STRING)
        .buildDecision()
      .build();
    // @formatter:on
    DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtensionRule.deployDecisionDefinition(modelInstance);


    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<DecisionVariableNameDto> variableResponse = getVariableNames("decision2", "1");

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.get(0).getName().endsWith("2"), is(true));
    assertThat(variableResponse.get(0).getId(), notNullValue());
    assertThat(variableResponse.get(0).getType(), is(VariableType.STRING));
  }

  @Test
  public void getVariableNamesSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    deployAndStartMultiTenantDecision(
      newArrayList(null, tenantId1, tenantId2)
    );
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    DecisionVariableNameRequestDto variableNameRequestDto = new DecisionVariableNameRequestDto();
    variableNameRequestDto.setDecisionDefinitionKey(DECISION_KEY);
    variableNameRequestDto.setDecisionDefinitionVersion(ALL_VERSIONS);
    variableNameRequestDto.setTenantIds(selectedTenants);
    List<DecisionVariableNameDto> variableResponse = getVariableNames(variableNameRequestDto);

    // then
    assertThat(variableResponse.size(), is(selectedTenants.size()));
  }

  @Test
  public void getVariableNamesForMultipleDefinitionVersions_onlyLastVersionIsConsidered() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = deployDecisionsWithStringVarNames(of("bar"));
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDecisionsWithStringVarNames(of("foo1", "foo2"));
    DecisionDefinitionEngineDto decisionDefinitionDto3 =
      deployDecisionsWithStringVarNames(of("var1", "var2", "var3"));

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<DecisionVariableNameDto> variableResponse =
      getVariableNames(
        decisionDefinitionDto.getKey(),
        of(decisionDefinitionDto.getVersionAsString(), decisionDefinitionDto3.getVersionAsString())
      );

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.get(0).getName(), is("var1"));
    assertThat(variableResponse.get(1).getName(), is("var2"));
    assertThat(variableResponse.get(2).getName(), is("var3"));
  }

  @Test
  public void getMoreThan10Variables() {
    // given
    List<String> varListWithMoreThan10Entries =
      IntStream.range(0, 11).boxed().map(Objects::toString).collect(Collectors.toList());
    DecisionDefinitionEngineDto decisionDefinition = deployDecisionsWithStringVarNames(varListWithMoreThan10Entries);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<DecisionVariableNameDto> variableResponse = getVariableNames(decisionDefinition);

    // then
    assertThat(variableResponse.size(), is(11));
  }

  @Test
  public void getVariablesForAllVersions_onlyLastVersionIsConsidered() {
    // given
    DecisionDefinitionEngineDto decisionDefinition = deployDecisionsWithStringVarNames(of("bar"));
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDecisionsWithStringVarNames(of("foo1", "foo2"));
    DecisionDefinitionEngineDto decisionDefinitionDto3 =
      deployDecisionsWithStringVarNames(of("var1", "var2", "var3"));
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<DecisionVariableNameDto> variableResponse = getVariableNames(decisionDefinition.getKey(), ALL_VERSIONS);

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.get(0).getName(), is("var1"));
    assertThat(variableResponse.get(1).getName(), is("var2"));
    assertThat(variableResponse.get(2).getName(), is("var3"));
  }

  @Test
  public void getVariableNamesForLatestVersions() {
    // given
    DecisionDefinitionEngineDto decisionDefinition = deployDecisionsWithStringVarNames(of("bar"));
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDecisionsWithStringVarNames(of("foo1", "foo2"));
    DecisionDefinitionEngineDto decisionDefinitionDto3 =
      deployDecisionsWithStringVarNames(of("var1", "var2", "var3"));
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<DecisionVariableNameDto> variableResponse = getVariableNames(decisionDefinition.getKey(), LATEST_VERSION);

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.get(0).getName(), is("var1"));
    assertThat(variableResponse.get(1).getName(), is("var2"));
    assertThat(variableResponse.get(2).getName(), is("var3"));
  }

  @Test
  public void noVariablesFromAnotherDecisionDefinition() {
    // given
    DecisionDefinitionEngineDto decisionDefinition = deployDecisionsWithStringVarNames(of("expectedVar"));
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDecisionsWithStringVarNames(of("notExpectedVar"));
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<DecisionVariableNameDto> variableResponse = getVariableNames(decisionDefinition);

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.get(0).getName(), is("expectedVar"));
  }

  @Test
  public void variablesAreSortedAccordingTheirOccurrence() {
    // given
    ImmutableList<String> expectedVars = of("var4", "var1", "var134124", "bar", "aaa");
    DecisionDefinitionEngineDto decisionDefinition =
      deployDecisionsWithStringVarNames(expectedVars);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<DecisionVariableNameDto> variableResponse = getVariableNames(decisionDefinition);

    // then
    assertThat(variableResponse.size(), is(expectedVars.size()));
    List<String> actualVars =
      variableResponse.stream().map(DecisionVariableNameDto::getName).collect(Collectors.toList());
    assertThat(actualVars, is(expectedVars));
  }

  @Test
  public void canRetrieveEveryType() {
    // given
    DecisionDefinitionEngineDto definitionEngine =
      deployDecisionsWithVarNames(nCopies(values().length, "var"), asList(values()));
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<DecisionVariableNameDto> variableResponse = getVariableNames(definitionEngine);

    // then
    VariableType[] expectedTypes =
      Arrays.stream(values())
        .map(typeRef -> VariableType.getTypeForId(typeRef.getId()))
        .toArray(VariableType[]::new);
    assertThat(variableResponse.size(), is(expectedTypes.length));
    List<VariableType> actualTypes = variableResponse.stream()
      .map(DecisionVariableNameDto::getType)
      .collect(Collectors.toList());
    assertThat(actualTypes, containsInAnyOrder(expectedTypes));
  }

  @Test
  public void variableWithSameNameAndDifferentType() {
    // given
    DecisionDefinitionEngineDto decisionDefinition = deployDecisionsWithVarNames(of("var", "var"), of(STRING, BOOLEAN));
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<DecisionVariableNameDto> variableResponse = getVariableNames(decisionDefinition);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.get(0).getName(), is("var"));
    assertThat(variableResponse.get(0).getType(), is(VariableType.STRING));
    assertThat(variableResponse.get(1).getName(), is("var"));
    assertThat(variableResponse.get(1).getType(), is(VariableType.BOOLEAN));
  }

  protected abstract DecisionDefinitionEngineDto deployDecisionsWithVarNames(List<String> varNames,
                                                                             List<DecisionTypeRef> types);

  protected abstract List<DecisionVariableNameDto> getVariableNames(DecisionVariableNameRequestDto variableRequestDto);

  private DecisionDefinitionEngineDto deployDecisionsWithStringVarNames(List<String> varNames) {
    return deployDecisionsWithVarNames(varNames, of(STRING));
  }

  private void deployDecisionsWithStringVarName(String varName) {
    deployDecisionsWithVarNames(of(varName), of(DecisionTypeRef.STRING));
  }

  private List<DecisionVariableNameDto> getVariableNames(DecisionDefinitionEngineDto decisionDefinition) {
    DecisionVariableNameRequestDto variableRequestDto = new DecisionVariableNameRequestDto();
    variableRequestDto.setDecisionDefinitionKey(decisionDefinition.getKey());
    variableRequestDto.setDecisionDefinitionVersions(of(decisionDefinition.getVersionAsString()));
    return getVariableNames(variableRequestDto);
  }

  private List<DecisionVariableNameDto> getVariableNames(String key, List<String> versions) {
    DecisionVariableNameRequestDto variableRequestDto = new DecisionVariableNameRequestDto();
    variableRequestDto.setDecisionDefinitionKey(key);
    variableRequestDto.setDecisionDefinitionVersions(versions);
    return getVariableNames(variableRequestDto);
  }

  private List<DecisionVariableNameDto> getVariableNames(String key, String version) {
    return getVariableNames(key, of(version));
  }

  private void deployAndStartMultiTenantDecision(final List<String> deployedTenants) {
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtensionRule.createTenant(tenantId));
    deployedTenants.forEach(tenant -> {
      String randomVarName = RandomStringUtils.randomAlphabetic(10);
      deployDecisionsWithStringVarName(randomVarName);
    });
  }

  private void startDecisionInstanceWithInputs(final DecisionDefinitionEngineDto decisionDefinitionDto,
                                               final double amountValue,
                                               final String category) {
    final HashMap<String, InputVariableEntry> inputs = createInputs(amountValue, category);
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), inputs);
  }

}
