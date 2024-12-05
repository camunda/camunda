/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.elasticsearch;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.zeebeimport.RecordsReaderHolder;
import io.camunda.webapps.schema.descriptors.operate.index.ImportPositionIndex;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ElasticsearchRecordsReaderIT extends OperateZeebeAbstractIT {
  @Autowired private RestHighLevelClient esClient;
  @Autowired private ImportPositionIndex importPositionIndex;
  @Autowired private RecordsReaderHolder recordsReaderHolder;

  @Test
  public void shouldWriteDefaultEmptyDefaultImportPositionDocumentsOnRecordReaderStart() {
    Awaitility.await()
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> {
              final var searchRequest =
                  new SearchRequest(importPositionIndex.getFullQualifiedName());
              searchRequest.source().size(100);

              final var documents = esClient.search(searchRequest, RequestOptions.DEFAULT);

              // all initial import position documents created for each record reader
              return documents.getHits().getHits().length
                  == recordsReaderHolder.getAllRecordsReaders().size();
            });
  }
}
