/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.camunda.optimize.test.util.decision.DmnModelGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.of;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static java.util.Collections.nCopies;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.test.util.decision.DecisionTypeRef.BOOLEAN;
import static org.camunda.optimize.test.util.decision.DecisionTypeRef.STRING;
import static org.camunda.optimize.test.util.decision.DecisionTypeRef.values;

public abstract class DecisionVariableNameRetrievalIT extends AbstractDecisionDefinitionIT {

  protected static final String DECISION_KEY = "aDecision";

  @Test
  public void getVariableNames() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = deployDecisionsWithStringVarNames(of("var1", "var2"));

    importAllEngineEntitiesFromScratch();

    // when
    List<DecisionVariableNameResponseDto> variableResponse = getVariableNames(decisionDefinitionDto);

    // then
    assertThat(variableResponse)
      .hasSize(2)
      .allSatisfy(varName -> assertThat(varName.getId()).isNotNull())
      .extracting(DecisionVariableNameResponseDto::getName, DecisionVariableNameResponseDto::getType)
      .containsExactly(
        Tuple.tuple("var1", VariableType.STRING),
        Tuple.tuple("var2", VariableType.STRING)
      );
  }

  @Test
  public void getVariableNames_multipleDefinitions() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionsWithStringVarNames(of("var1", "var2"));
    // duplicate variable "var2" should not appear twice
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDecisionsWithStringVarNames(of("var2", "var3", "var4"));

    importAllEngineEntitiesFromScratch();

    // when
    List<DecisionVariableNameResponseDto> variableResponse = getVariableNames(Arrays.asList(
      decisionDefinitionDto1, decisionDefinitionDto2
    ));

    // then
    assertThat(variableResponse)
      .extracting(DecisionVariableNameResponseDto::getName)
      .containsExactly("var1", "var2", "var3", "var4");
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
      engineIntegrationExtension.deployDecisionDefinition(modelInstance);


    importAllEngineEntitiesFromScratch();

    // when
    List<DecisionVariableNameResponseDto> variableResponse = getVariableNames("decision2", "1");

    // then
    assertThat(variableResponse)
      .hasSize(1)
      .singleElement()
      .satisfies(varName -> {
        assertThat(varName.getId()).isNotNull();
        assertThat(varName.getName()).endsWith("2");
        assertThat(varName.getType()).isEqualTo(VariableType.STRING);
      });
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
    importAllEngineEntitiesFromScratch();

    // when
    DecisionVariableNameRequestDto variableNameRequestDto = new DecisionVariableNameRequestDto();
    variableNameRequestDto.setDecisionDefinitionKey(DECISION_KEY);
    variableNameRequestDto.setDecisionDefinitionVersion(ALL_VERSIONS);
    variableNameRequestDto.setTenantIds(selectedTenants);
    List<DecisionVariableNameResponseDto> variableResponse = getVariableNames(variableNameRequestDto);

    // then
    assertThat(variableResponse).hasSameSizeAs(selectedTenants);
  }

  @Test
  public void getVariableNamesForMultipleDefinitionVersions_onlyLastVersionIsConsidered() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = deployDecisionsWithStringVarNames(of("bar"));
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDecisionsWithStringVarNames(of("foo1", "foo2"));
    DecisionDefinitionEngineDto decisionDefinitionDto3 =
      deployDecisionsWithStringVarNames(of("var1", "var2", "var3"));

    importAllEngineEntitiesFromScratch();

    // when
    List<DecisionVariableNameResponseDto> variableResponse =
      getVariableNames(
        decisionDefinitionDto.getKey(),
        of(decisionDefinitionDto.getVersionAsString(), decisionDefinitionDto3.getVersionAsString())
      );

    // then
    assertThat(variableResponse)
      .hasSize(3)
      .extracting(DecisionVariableNameResponseDto::getName)
      .containsExactly("var1", "var2", "var3");
  }

  @Test
  public void getMoreThan10Variables() {
    // given
    List<String> varListWithMoreThan10Entries =
      IntStream.range(0, 11).boxed().map(Objects::toString).collect(Collectors.toList());
    DecisionDefinitionEngineDto decisionDefinition = deployDecisionsWithStringVarNames(varListWithMoreThan10Entries);
    importAllEngineEntitiesFromScratch();

    // when
    List<DecisionVariableNameResponseDto> variableResponse = getVariableNames(decisionDefinition);

    // then
    assertThat(variableResponse).hasSize(11);
  }

  @Test
  public void getVariablesForAllVersions_onlyLastVersionIsConsidered() {
    // given
    DecisionDefinitionEngineDto decisionDefinition = deployDecisionsWithStringVarNames(of("bar"));
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDecisionsWithStringVarNames(of("foo1", "foo2"));
    DecisionDefinitionEngineDto decisionDefinitionDto3 =
      deployDecisionsWithStringVarNames(of("var1", "var2", "var3"));
    importAllEngineEntitiesFromScratch();

    // when
    List<DecisionVariableNameResponseDto> variableResponse = getVariableNames(
      decisionDefinition.getKey(),
      ALL_VERSIONS
    );

    // then
    assertThat(variableResponse)
      .hasSize(3)
      .extracting(DecisionVariableNameResponseDto::getName)
      .containsExactly("var1", "var2", "var3");
  }

  @Test
  public void getVariableNamesForLatestVersions() {
    // given
    DecisionDefinitionEngineDto decisionDefinition = deployDecisionsWithStringVarNames(of("bar"));
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDecisionsWithStringVarNames(of("foo1", "foo2"));
    DecisionDefinitionEngineDto decisionDefinitionDto3 =
      deployDecisionsWithStringVarNames(of("var1", "var2", "var3"));
    importAllEngineEntitiesFromScratch();

    // when
    List<DecisionVariableNameResponseDto> variableResponse = getVariableNames(
      decisionDefinition.getKey(),
      LATEST_VERSION
    );

    // then
    assertThat(variableResponse)
      .hasSize(3)
      .extracting(DecisionVariableNameResponseDto::getName)
      .containsExactly("var1", "var2", "var3");
  }

  @Test
  public void noVariablesFromAnotherDecisionDefinition() {
    // given
    DecisionDefinitionEngineDto decisionDefinition = deployDecisionsWithStringVarNames(of("expectedVar"));
    deployDecisionsWithStringVarNames(of("notExpectedVar"));
    importAllEngineEntitiesFromScratch();

    // when
    List<DecisionVariableNameResponseDto> variableResponse = getVariableNames(decisionDefinition);

    // then
    assertThat(variableResponse)
      .hasSize(1)
      .singleElement()
      .satisfies(var -> assertThat(var.getName()).isEqualTo("expectedVar"));
  }

  @Test
  public void variablesAreSortedAccordingTheirOccurrence() {
    // given
    ImmutableList<String> expectedVars = of("var4", "var1", "var134124", "bar", "aaa");
    DecisionDefinitionEngineDto decisionDefinition =
      deployDecisionsWithStringVarNames(expectedVars);
    importAllEngineEntitiesFromScratch();

    // when
    List<DecisionVariableNameResponseDto> variableResponse = getVariableNames(decisionDefinition);

    // then
    assertThat(variableResponse).hasSize(expectedVars.size());
    List<String> actualVars =
      variableResponse.stream().map(DecisionVariableNameResponseDto::getName).collect(Collectors.toList());
    assertThat(actualVars).isEqualTo(expectedVars);
  }

  @Test
  public void canRetrieveEveryType() {
    // given
    DecisionDefinitionEngineDto definitionEngine =
      deployDecisionsWithVarNames(nCopies(values().length, "var"), asList(values()));
    importAllEngineEntitiesFromScratch();

    // when
    List<DecisionVariableNameResponseDto> variableResponse = getVariableNames(definitionEngine);

    // then
    VariableType[] expectedTypes =
      Arrays.stream(values())
        .map(typeRef -> VariableType.getTypeForId(typeRef.getId()))
        .toArray(VariableType[]::new);
    assertThat(variableResponse).hasSize(expectedTypes.length);
    List<VariableType> actualTypes = variableResponse.stream()
      .map(DecisionVariableNameResponseDto::getType)
      .collect(Collectors.toList());
    assertThat(actualTypes).containsExactlyInAnyOrder(expectedTypes);
  }

  @Test
  public void variableWithSameNameAndDifferentType() {
    // given
    DecisionDefinitionEngineDto decisionDefinition = deployDecisionsWithVarNames(of("var", "var"), of(STRING, BOOLEAN));
    importAllEngineEntitiesFromScratch();

    // when
    List<DecisionVariableNameResponseDto> variableResponse = getVariableNames(decisionDefinition);

    // then
    assertThat(variableResponse)
      .hasSize(2)
      .extracting(DecisionVariableNameResponseDto::getName, DecisionVariableNameResponseDto::getType)
      .containsExactly(
        Tuple.tuple("var", VariableType.STRING),
        Tuple.tuple("var", VariableType.BOOLEAN)
      );
  }

  protected abstract DecisionDefinitionEngineDto deployDecisionsWithVarNames(List<String> varNames,
                                                                             List<DecisionTypeRef> types);

  protected abstract List<DecisionVariableNameResponseDto> getVariableNames(DecisionVariableNameRequestDto variableRequestDto);

  private DecisionDefinitionEngineDto deployDecisionsWithStringVarNames(List<String> varNames) {
    return deployDecisionsWithVarNames(varNames, of(STRING));
  }

  private void deployDecisionsWithStringVarName(String varName) {
    deployDecisionsWithVarNames(of(varName), of(DecisionTypeRef.STRING));
  }

  protected List<DecisionVariableNameResponseDto> getVariableNames(DecisionDefinitionEngineDto decisionDefinition) {
    return getVariableNames(Collections.singletonList(decisionDefinition));
  }

  protected abstract List<DecisionVariableNameResponseDto> getVariableNames(List<DecisionDefinitionEngineDto> decisionDefinitions);

  protected abstract List<DecisionVariableNameResponseDto> getVariableNames(String key, List<String> versions);

  private List<DecisionVariableNameResponseDto> getVariableNames(String key, String version) {
    return getVariableNames(key, of(version));
  }

  private void deployAndStartMultiTenantDecision(final List<String> deployedTenants) {
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants.forEach(tenant -> {
      String randomVarName = RandomStringUtils.randomAlphabetic(10);
      deployDecisionsWithStringVarName(randomVarName);
    });
  }

}
