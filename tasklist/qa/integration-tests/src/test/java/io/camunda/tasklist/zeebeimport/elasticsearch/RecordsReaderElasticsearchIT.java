/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.elasticsearch;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.zeebeimport.RecordsReaderHolder;
import io.camunda.webapps.schema.descriptors.tasklist.index.TasklistImportPositionIndex;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RecordsReaderElasticsearchIT extends TasklistZeebeIntegrationTest {
  @Autowired private RestHighLevelClient tasklistEsClient;
  @Autowired private TasklistImportPositionIndex importPositionIndex;
  @Autowired private RecordsReaderHolder recordsReaderHolder;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @Test
  public void shouldWriteDefaultEmptyDefaultImportPositionDocumentsOnRecordReaderStart() {
    Awaitility.await()
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> {
              final var searchRequest =
                  new SearchRequest(importPositionIndex.getFullQualifiedName());
              searchRequest.source().size(100);

              final var documents = tasklistEsClient.search(searchRequest, RequestOptions.DEFAULT);

              // all initial import position documents created for each record reader
              return documents.getHits().getHits().length
                  == recordsReaderHolder.getAllRecordsReaders().size();
            });
  }
}
