/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class DecisionDefinitionRetrievalIT extends AbstractDecisionDefinitionIT {

  private final static String DECISION_DEFINITION_KEY = "aDecision";

  @Test
  public void getDecisionDefinitionsWithMoreThenTen() {
    for (int i = 0; i < 11; i++) {
      // given
      deployAndStartSimpleDecisionDefinition(DECISION_DEFINITION_KEY + i);
    }
    embeddedOptimizeExtension.getConfigurationService().setEngineImportDecisionDefinitionXmlMaxPageSize(11);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<DecisionDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDecisionDefinitionsRequest()
        .executeAndReturnList(DecisionDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    assertThat(definitions.size(), is(11));
  }

  @Test
  public void getDecisionDefinitionsWithoutXml() {
    // given
    String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      deployAndStartSimpleDecisionDefinition(decisionDefinitionKey);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<DecisionDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDecisionDefinitionsRequest()
        .addSingleQueryParam("includeXml", false)
        .executeAndReturnList(DecisionDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(decisionDefinitionEngineDto.getId()));
    assertThat(definitions.get(0).getKey(), is(decisionDefinitionKey));
    assertThat(definitions.get(0).getDmn10Xml(), nullValue());
  }

  @Test
  public void getDecisionDefinitionsWithXml() {
    // given
    final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionDefinitionKey);
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = engineIntegrationExtension.deployDecisionDefinition(modelInstance);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<DecisionDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDecisionDefinitionsRequest()
        .addSingleQueryParam("includeXml", true)
        .executeAndReturnList(DecisionDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(decisionDefinitionEngineDto.getId()));
    assertThat(definitions.get(0).getKey(), is(decisionDefinitionKey));
    assertThat(definitions.get(0).getDmn10Xml(), is(Dmn.convertToString(modelInstance)));
  }

  @Test
  public void getDecisionDefinitionsOnlyIncludeTheOnesWhereTheXmlIsImported() {
    // given
    final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      deployAndStartSimpleDecisionDefinition(decisionDefinitionKey);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();

    addDecisionDefinitionWithoutXmlToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<DecisionDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDecisionDefinitionsRequest()
        .addSingleQueryParam("includeXml", false)
        .executeAndReturnList(DecisionDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(decisionDefinitionEngineDto.getId()));
  }

  @Test
  public void getDecisionDefinitionXmlByKeyAndVersion() {
    // given
    final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionDefinitionKey);
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = engineIntegrationExtension.deployDecisionDefinition(modelInstance);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    String actualXml = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDecisionDefinitionXmlRequest(
        decisionDefinitionEngineDto.getKey(),
        decisionDefinitionEngineDto.getVersion()
      )
      .execute(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(actualXml, is(Dmn.convertToString(modelInstance)));
  }

  @Test
  public void getDecisionDefinitionXmlByKeyAndAllVersionReturnsLatest() {
    // given
    final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
    // first version
    final DmnModelInstance modelInstance1 = createSimpleDmnModel(decisionDefinitionKey);
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto1 =
      engineIntegrationExtension.deployDecisionDefinition(modelInstance1);
    // second version
    final DmnModelInstance modelInstance2 = createSimpleDmnModel(decisionDefinitionKey);
    modelInstance2.getDefinitions().getDrgElements().stream().findFirst()
      .ifPresent(drgElement -> drgElement.setName("Add name to ensure that this is the latest version!"));
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto2 =
      engineIntegrationExtension.deployDecisionDefinition(modelInstance2);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final String actualXml = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDecisionDefinitionXmlRequest(decisionDefinitionEngineDto1.getKey(), ALL_VERSIONS)
      .execute(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(actualXml, is(Dmn.convertToString(modelInstance2)));
  }

  @Test
  public void getDecisionDefinitionXmlByKeyAndAllVersionReturnsLatestWithMoreThanTen() {
    // given
    final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();

    // given 12 definitions (11 + 1 latest)
    for (int i = 0; i < 11; i++) {
      deployAndStartSimpleDecisionDefinition(decisionDefinitionKey);
    }

    final DmnModelInstance latestModelInstance = createSimpleDmnModel(decisionDefinitionKey);
    latestModelInstance.getDefinitions().getDrgElements().stream().findFirst()
      .ifPresent(drgElement -> drgElement.setName("Add name to ensure that this is the latest version!"));
    engineIntegrationExtension.deployDecisionDefinition(latestModelInstance);

    embeddedOptimizeExtension.getConfigurationService().setEngineImportDecisionDefinitionXmlMaxPageSize(12);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    String actualXml =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetDecisionDefinitionXmlRequest(decisionDefinitionKey, ALL_VERSIONS)
        .execute(String.class, Response.Status.OK.getStatusCode());

    // then: we get the latest version xml
    assertThat(actualXml, is(Dmn.convertToString(latestModelInstance)));
  }


  private void addDecisionDefinitionWithoutXmlToElasticsearch() {
    final DecisionDefinitionOptimizeDto decisionDefinitionWithoutXml = DecisionDefinitionOptimizeDto.builder()
      .id("aDecDefId")
      .key("aDecDefKey")
      .version("aDevDefVersion")
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(DECISION_DEFINITION_INDEX_NAME, "fooId", decisionDefinitionWithoutXml);
  }

}
