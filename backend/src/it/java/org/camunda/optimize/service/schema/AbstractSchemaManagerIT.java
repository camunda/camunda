/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.schema;

import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.elasticsearch.ElasticsearchStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractSchemaManagerIT extends AbstractPlatformIT {

  protected DatabaseClient prefixAwareDatabaseClient;
  protected OptimizeIndexNameService indexNameService;

  protected abstract void initializeSchema();

  @BeforeEach
  public void setUp() {
    // given
    databaseIntegrationTestExtension.cleanAndVerify();
    prefixAwareDatabaseClient = embeddedOptimizeExtension.getOptimizeDatabaseClient();
    indexNameService = prefixAwareDatabaseClient.getIndexNameService();
  }

  @Test
  public void schemaIsNotInitializedTwice() {
    // when I initialize schema twice
    initializeSchema();
    initializeSchema();

    // then throws no errors
    assertThatNoException();
  }

  @Test
  public void onlyAcceptDocumentsThatComplyWithTheSchema() {
    // given schema is created
    initializeSchema();

    // then an exception is thrown when we add an event with an undefined type in schema
    ExtendedFlowNodeEventDto extendedEventDto = new ExtendedFlowNodeEventDto();
    assertThatThrownBy(() -> databaseIntegrationTestExtension.addEntryToDatabase(
      DatabaseConstants.METADATA_INDEX_NAME,
      "12312412",
      extendedEventDto
    )).isInstanceOf(ElasticsearchStatusException.class);
  }

  protected void assertIndexExists(String indexName) {
    assertThat(databaseIntegrationTestExtension.indexExists(indexName)).isTrue();
  }

}
