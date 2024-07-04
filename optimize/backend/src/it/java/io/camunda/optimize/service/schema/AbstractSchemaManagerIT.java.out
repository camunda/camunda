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
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.assertj.core.api.Assertions.assertThatNoException;
// import static org.assertj.core.api.Assertions.assertThatThrownBy;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.service.db.DatabaseClient;
// import io.camunda.optimize.service.db.DatabaseConstants;
// import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
//
// public abstract class AbstractSchemaManagerIT extends AbstractPlatformIT {
//
//   protected DatabaseClient prefixAwareDatabaseClient;
//   protected OptimizeIndexNameService indexNameService;
//
//   protected abstract void initializeSchema();
//
//   protected abstract <T extends Exception> Class<T> expectedDatabaseExtensionStatusException();
//
//   @BeforeEach
//   public void setUp() {
//     // given
//     databaseIntegrationTestExtension.cleanAndVerify();
//     prefixAwareDatabaseClient = embeddedOptimizeExtension.getOptimizeDatabaseClient();
//     indexNameService = prefixAwareDatabaseClient.getIndexNameService();
//   }
//
//   @Test
//   public void schemaIsNotInitializedTwice() {
//     // when I initialize schema twice
//     initializeSchema();
//     initializeSchema();
//
//     // then throws no errors
//     assertThatNoException();
//   }
//
//   @Test
//   public void onlyAcceptDocumentsThatComplyWithTheSchema() {
//     // given schema is created
//     initializeSchema();
//
//     // then an exception is thrown when we add an event with an undefined type in schema
//     ExtendedFlowNodeEventDto extendedEventDto = new ExtendedFlowNodeEventDto();
//     assertThatThrownBy(
//             () ->
//                 databaseIntegrationTestExtension.addEntryToDatabase(
//                     DatabaseConstants.METADATA_INDEX_NAME, "12312412", extendedEventDto))
//         .isInstanceOf(expectedDatabaseExtensionStatusException());
//   }
//
//   protected void assertIndexExists(String indexName) {
//     assertThat(databaseIntegrationTestExtension.indexExists(indexName)).isTrue();
//   }
// }
