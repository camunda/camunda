/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.opensearch;

import io.camunda.operate.conditions.DatabaseCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.zeebeimport.RecordsReaderHolder;
import io.camunda.webapps.schema.descriptors.operate.index.ImportPositionIndex;
import io.camunda.webapps.schema.entities.operate.ImportPositionEntity;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.Test;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = DatabaseCondition.DATABASE_PROPERTY + "=opensearch")
public class OpensearchRecordsReaderIT extends OperateZeebeAbstractIT {
  @Autowired private RichOpenSearchClient osClient;
  @Autowired private ImportPositionIndex importPositionIndex;
  @Autowired private RecordsReaderHolder recordsReaderHolder;

  @Test
  public void shouldWriteDefaultEmptyDefaultImportPositionDocumentsOnRecordReaderStart() {
    Awaitility.await()
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> {
              final var searchRequestBuilder =
                  new SearchRequest.Builder()
                      .size(100)
                      .index(importPositionIndex.getFullQualifiedName());
              final var documents =
                  osClient.doc().search(searchRequestBuilder, ImportPositionEntity.class);

              // all initial import position documents created for each record reader
              return documents.hits().hits().size()
                  == recordsReaderHolder.getAllRecordsReaders().size();
            });
  }
}
