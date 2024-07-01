/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.schema;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
// import static
// io.camunda.optimize.service.db.schema.OptimizeIndexNameService.getOptimizeIndexAliasForIndexNameAndPrefix;
// import static
// io.camunda.optimize.service.db.schema.OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.service.db.DatabaseClient;
// import io.camunda.optimize.service.db.schema.IndexMappingCreator;
// import io.camunda.optimize.test.it.extension.DatabaseIntegrationTestExtension;
// import io.camunda.optimize.util.BpmnModels;
// import java.io.IOException;
// import java.util.List;
// import java.util.UUID;
// import org.camunda.bpm.model.bpmn.BpmnModelInstance;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Order;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.RegisterExtension;
//
// @Tag(OPENSEARCH_PASSING)
// public class CustomIndexPrefixIT extends AbstractPlatformIT {
//   private static final String CUSTOM_PREFIX = UUID.randomUUID().toString().substring(0, 5);
//
//   @RegisterExtension
//   @Order(2)
//   public DatabaseIntegrationTestExtension customPrefixDatabaseIntegrationTestExtension =
//       new DatabaseIntegrationTestExtension(CUSTOM_PREFIX);
//
//   private DatabaseClient prefixAwareDatabaseClient;
//
//   @BeforeEach
//   public void setUp() {
//     prefixAwareDatabaseClient = embeddedOptimizeExtension.getOptimizeDatabaseClient();
//   }
//
//   @Test
//   public void optimizeCustomPrefixIndexExistsAfterSchemaInitialization() {
//     // given
//     // Setting both configurations so that this is set properly regardless of which database is
//     // active
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getElasticSearchConfiguration()
//         .setIndexPrefix(CUSTOM_PREFIX);
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getOpenSearchConfiguration()
//         .setIndexPrefix(CUSTOM_PREFIX);
//     embeddedOptimizeExtension.reloadConfiguration();
//
//     // when
//     initializeSchema();
//
//     // then
//     assertThat(prefixAwareDatabaseClient.getIndexNameService().getIndexPrefix())
//         .isEqualTo(CUSTOM_PREFIX);
//     assertThat(
//             embeddedOptimizeExtension
//                 .getDatabaseSchemaManager()
//                 .schemaExists(prefixAwareDatabaseClient))
//         .isTrue();
//   }
//
//   @Test
//   public void allTypesWithPrefixExistAfterSchemaInitialization() throws IOException {
//     // given
//     // Setting both configurations so that this is set properly regardless of which database is
//     // active
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getElasticSearchConfiguration()
//         .setIndexPrefix(CUSTOM_PREFIX);
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getOpenSearchConfiguration()
//         .setIndexPrefix(CUSTOM_PREFIX);
//     embeddedOptimizeExtension.reloadConfiguration();
//
//     // when
//     initializeSchema();
//
//     // then
//     final List<IndexMappingCreator<?>> mappings =
//         embeddedOptimizeExtension.getDatabaseSchemaManager().getMappings();
//     assertThat(mappings).hasSize(28);
//     for (final IndexMappingCreator mapping : mappings) {
//       final String expectedAliasName =
//           getOptimizeIndexAliasForIndexNameAndPrefix(mapping.getIndexName(), CUSTOM_PREFIX);
//       final String expectedIndexName =
//           getOptimizeIndexOrTemplateNameForAliasAndVersion(
//                   expectedAliasName, String.valueOf(mapping.getVersion()))
//               + mapping.getIndexNameInitialSuffix();
//
//       assertThat(
//               embeddedOptimizeExtension
//                   .getDatabaseSchemaManager()
//                   .indexExists(prefixAwareDatabaseClient, expectedAliasName))
//           .isTrue();
//       assertThat(
//               embeddedOptimizeExtension
//                   .getDatabaseSchemaManager()
//                   .indexExists(prefixAwareDatabaseClient, expectedIndexName))
//           .isTrue();
//     }
//   }
//
//   @Test
//   public void optimizeIndexDataIsIsolated() {
//     // given
//     deploySimpleProcess();
//     importAllEngineEntitiesFromScratch();
//
//     final String indexPrefix =
//         customPrefixDatabaseIntegrationTestExtension.getIndexNameService().getIndexPrefix();
//
//     // when
//     // Set values for both ES and OS, the proper one will be used depending on which database is
//     // active
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getElasticSearchConfiguration()
//         .setIndexPrefix(indexPrefix);
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getOpenSearchConfiguration()
//         .setIndexPrefix(indexPrefix);
//     embeddedOptimizeExtension.reloadConfiguration();
//     initializeSchema();
//
//     deploySimpleProcess();
//
//     importAllEngineEntitiesFromScratch();
//     customPrefixDatabaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // then
//     assertThat(databaseIntegrationTestExtension.getDocumentCountOf(PROCESS_INSTANCE_MULTI_ALIAS))
//         .isEqualTo(1);
//     assertThat(
//             customPrefixDatabaseIntegrationTestExtension.getDocumentCountOf(
//                 PROCESS_INSTANCE_MULTI_ALIAS))
//         .isEqualTo(2);
//   }
//
//   private void deploySimpleProcess() {
//     engineIntegrationExtension.deployAndStartProcess(createSimpleProcess());
//   }
//
//   private BpmnModelInstance createSimpleProcess() {
//     return BpmnModels.getSingleServiceTaskProcess("aProcess");
//   }
//
//   private void initializeSchema() {
//     embeddedOptimizeExtension
//         .getDatabaseSchemaManager()
//         .initializeSchema(prefixAwareDatabaseClient);
//   }
// }
