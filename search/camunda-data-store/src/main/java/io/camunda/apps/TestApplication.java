/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.apps;

import io.camunda.data.clients.DataStoreClient;
import io.camunda.data.clients.core.DataStoreRequestBuilders;
import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.data.clients.core.search.DataStoreSearchHit;
import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.data.clients.query.DataStoreQueryBuilders;

public class TestApplication {

  public static void main(String[] args) {

    final DataStoreClient client = null;

    // create search request
    final DataStoreQuery query =
        DataStoreQueryBuilders.query((q) -> q.term((t) -> t.field("name").value("foo")));
    final DataStoreSearchRequest searchRequest =
        DataStoreRequestBuilders.searchRequest(
            (r) ->
                r.index("my-index")
                    .query(query)
                    .sort((s) -> s.field((f) -> f.field("endDate").asc())));

    // execute search request
    final var searchResponse = client.search(searchRequest, MyEntity.class);

    // Exception Handling
    if (searchResponse.isLeft()) {
      final var exception = searchResponse.getLeft();
      throw new RuntimeException("something went wrong", exception);
    }

    // Consume Result
    final DataStoreSearchResponse<MyEntity> result = searchResponse.get();
    final long total = result.totalHits();
    final DataStoreSearchHit<MyEntity> hit = result.hits().get(0);

    // meta data
    final var index = hit.index();
    final var routing = hit.routing();

    // source
    final MyEntity entity = hit.source();
  }

  static final record MyEntity(String foo, String bar) {}
}
