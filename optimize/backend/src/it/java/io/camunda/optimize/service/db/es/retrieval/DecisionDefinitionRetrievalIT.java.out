/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.retrieval;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
// import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
// import static io.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
// import io.camunda.optimize.service.db.es.report.decision.AbstractDecisionDefinitionIT;
// import jakarta.ws.rs.core.Response;
// import java.util.List;
// import org.camunda.bpm.model.dmn.Dmn;
// import org.camunda.bpm.model.dmn.DmnModelInstance;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class DecisionDefinitionRetrievalIT extends AbstractDecisionDefinitionIT {
//
//   private static final String DECISION_DEFINITION_KEY = "aDecision";
//
//   @Test
//   public void getDecisionDefinitionsWithMoreThanTen() {
//     for (int i = 0; i < 11; i++) {
//       // given
//       deployAndStartSimpleDecisionDefinition(DECISION_DEFINITION_KEY + i);
//     }
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .setEngineImportDecisionDefinitionXmlMaxPageSize(11);
//     importAllEngineEntitiesFromScratch();
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // when
//     final List<DecisionDefinitionOptimizeDto> definitions =
//         definitionClient.getAllDecisionDefinitions();
//
//     assertThat(definitions).hasSize(11);
//   }
//
//   @Test
//   public void getDecisionDefinitionsWithoutXml() {
//     // given
//     final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
//     final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
//         deployAndStartSimpleDecisionDefinition(decisionDefinitionKey);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final List<DecisionDefinitionOptimizeDto> definitions =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildGetDecisionDefinitionsRequest()
//             .addSingleQueryParam("includeXml", false)
//             .executeAndReturnList(
//                 DecisionDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
//
//     // then
//     assertThat(definitions).hasSize(1);
//     assertThat(definitions.get(0).getId()).isEqualTo(decisionDefinitionEngineDto.getId());
//     assertThat(definitions.get(0).getKey()).isEqualTo(decisionDefinitionKey);
//     assertThat(definitions.get(0).getDmn10Xml()).isNull();
//   }
//
//   @Test
//   public void getDecisionDefinitionsWithXml() {
//     // given
//     final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
//     final DmnModelInstance modelInstance = createSimpleDmnModel(decisionDefinitionKey);
//     final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
//         engineIntegrationExtension.deployDecisionDefinition(modelInstance);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final List<DecisionDefinitionOptimizeDto> definitions =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildGetDecisionDefinitionsRequest()
//             .addSingleQueryParam("includeXml", true)
//             .executeAndReturnList(
//                 DecisionDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
//
//     // then
//     assertThat(definitions).hasSize(1);
//     assertThat(definitions.get(0).getId()).isEqualTo(decisionDefinitionEngineDto.getId());
//     assertThat(definitions.get(0).getKey()).isEqualTo(decisionDefinitionKey);
//     assertThat(definitions.get(0).getDmn10Xml()).isEqualTo(Dmn.convertToString(modelInstance));
//   }
//
//   @Test
//   public void getDecisionDefinitionsOnlyIncludeTheOnesWhereTheXmlIsImported() {
//     // given
//     final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
//     final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
//         deployAndStartSimpleDecisionDefinition(decisionDefinitionKey);
//
//     importAllEngineEntitiesFromScratch();
//
//     addDecisionDefinitionWithoutXmlToElasticsearch();
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // when
//     final List<DecisionDefinitionOptimizeDto> definitions =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildGetDecisionDefinitionsRequest()
//             .addSingleQueryParam("includeXml", false)
//             .executeAndReturnList(
//                 DecisionDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
//
//     // then
//     assertThat(definitions).hasSize(1);
//     assertThat(definitions.get(0).getId()).isEqualTo(decisionDefinitionEngineDto.getId());
//   }
//
//   @Test
//   public void getDecisionDefinitionXmlByKeyAndVersion() {
//     // given
//     final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
//     final DmnModelInstance modelInstance = createSimpleDmnModel(decisionDefinitionKey);
//     final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
//         engineIntegrationExtension.deployDecisionDefinition(modelInstance);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final String actualXml =
//         definitionClient.getDecisionDefinitionXml(
//             decisionDefinitionEngineDto.getKey(),
// decisionDefinitionEngineDto.getVersionAsString());
//
//     // then
//     assertThat(actualXml).isEqualTo(Dmn.convertToString(modelInstance));
//   }
//
//   @Test
//   public void getDecisionDefinitionXmlByKeyAndAllVersionReturnsLatest() {
//     // given
//     final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
//     // first version
//     final DmnModelInstance modelInstance1 = createSimpleDmnModel(decisionDefinitionKey);
//     final DecisionDefinitionEngineDto decisionDefinitionEngineDto1 =
//         engineIntegrationExtension.deployDecisionDefinition(modelInstance1);
//     // second version
//     final DmnModelInstance modelInstance2 = createSimpleDmnModel(decisionDefinitionKey);
//     modelInstance2.getDefinitions().getDrgElements().stream()
//         .findFirst()
//         .ifPresent(
//             drgElement ->
//                 drgElement.setName("Add name to ensure that this is the latest version!"));
//     engineIntegrationExtension.deployDecisionDefinition(modelInstance2);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final String actualXml =
//         definitionClient.getDecisionDefinitionXml(
//             decisionDefinitionEngineDto1.getKey(), ALL_VERSIONS);
//
//     // then
//     assertThat(actualXml).isEqualTo(Dmn.convertToString(modelInstance2));
//   }
//
//   @Test
//   public void getDecisionDefinitionXmlByKeyAndAllVersionReturnsLatestWithMoreThanTen() {
//     // given
//     final String decisionDefinitionKey = DECISION_DEFINITION_KEY + System.currentTimeMillis();
//
//     // given 12 definitions (11 + 1 latest)
//     for (int i = 0; i < 11; i++) {
//       deployAndStartSimpleDecisionDefinition(decisionDefinitionKey);
//     }
//
//     final DmnModelInstance latestModelInstance = createSimpleDmnModel(decisionDefinitionKey);
//     latestModelInstance.getDefinitions().getDrgElements().stream()
//         .findFirst()
//         .ifPresent(
//             drgElement ->
//                 drgElement.setName("Add name to ensure that this is the latest version!"));
//     engineIntegrationExtension.deployDecisionDefinition(latestModelInstance);
//
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .setEngineImportDecisionDefinitionXmlMaxPageSize(12);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final String actualXml =
//         definitionClient.getDecisionDefinitionXml(decisionDefinitionKey, ALL_VERSIONS);
//
//     // then: we get the latest version xml
//     assertThat(actualXml).isEqualTo(Dmn.convertToString(latestModelInstance));
//   }
//
//   private void addDecisionDefinitionWithoutXmlToElasticsearch() {
//     final DecisionDefinitionOptimizeDto decisionDefinitionWithoutXml =
//         DecisionDefinitionOptimizeDto.builder()
//             .id("aDecDefId")
//             .key("aDecDefKey")
//             .version("aDevDefVersion")
//             .build();
//     databaseIntegrationTestExtension.addEntryToDatabase(
//         DECISION_DEFINITION_INDEX_NAME, "fooId", decisionDefinitionWithoutXml);
//   }
// }
