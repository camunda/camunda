/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.util.opensearch;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.indexRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.util.SearchClientTestHelper;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.util.Map;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.IndexRequest.Builder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;

@Conditional(OpensearchCondition.class)
public class OpenSearchClientTestHelper implements SearchClientTestHelper {

  @Autowired private RichOpenSearchClient openSearchClient;

  @Override
  public void setClientRetries(final int retries) {
    // currently not implemented, tests that expect this behavior are currently
    // ignored for Opensearch
  }

  @Override
  public void createDocument(
      final String indexName, final String id, final Map<String, Object> document) {
    createDocument(indexName, id, null, document);
  }

  @Override
  public void createDocument(
      String indexName, String id, String routing, Map<String, Object> document) {

    final Builder<Object> requestBuilder =
        indexRequestBuilder(indexName)
            .id(id)
            .routing(routing)
            .document(document)
            .refresh(Refresh.True);

    openSearchClient.doc().index(requestBuilder);
  }

  @Override
  public void refreshAllIndexes() {
    openSearchClient.index().refresh("*");
  }
}
