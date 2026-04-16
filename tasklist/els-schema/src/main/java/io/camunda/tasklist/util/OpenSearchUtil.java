/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.CollectionUtil.throwAwayNullElements;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.util.List;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryVariant;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.util.ObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class OpenSearchUtil {

  public static final String SCROLL_KEEP_ALIVE_MS = "60000ms";
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchUtil.class);

  public static void clearScroll(final String scrollId, final OpenSearchClient osClient) {
    if (scrollId != null) {
      // clear the scroll
      final ClearScrollRequest clearScrollRequest =
          new ClearScrollRequest.Builder().scrollId(scrollId).build();

      try {
        osClient.clearScroll(clearScrollRequest);
      } catch (final Exception e) {
        LOGGER.warn("Error occurred when clearing the scroll with id [{}]", scrollId, e);
      }
    }
  }

  public static Query joinWithAnd(final ObjectBuilder... queries) {
    final List<ObjectBuilder> notNullQueries = throwAwayNullElements(queries);
    if (notNullQueries.size() == 0) {
      return new Query.Builder().build();
    }
    final BoolQuery.Builder boolQ = boolQuery();
    for (final ObjectBuilder queryBuilder : notNullQueries) {
      final var query = queryBuilder.build();

      if (query instanceof final QueryVariant qv) {
        boolQ.must(qv.toQuery());
      } else if (query instanceof final Query q) {
        boolQ.must(q);
      } else {
        throw new TasklistRuntimeException("Queries should be of type [Query] or [QueryVariant]");
      }
    }
    return new Query.Builder().bool(boolQ.build()).build();
  }

  public static Query createMatchNoneQuery() {
    final BoolQuery boolQuery =
        new BoolQuery.Builder()
            .must(must -> must.matchNone(none -> none.queryName("matchNone")))
            .build();
    return boolQuery.toQuery();
  }

  public static Query joinWithAnd(final Query... queries) {
    final List<Query> notNullQueries = throwAwayNullElements(queries);
    if (notNullQueries.size() == 0) {
      return new Query.Builder().build();
    }
    final BoolQuery.Builder boolQ = boolQuery();
    for (final Query queryBuilder : notNullQueries) {
      final var query = queryBuilder;

      if (query instanceof final QueryVariant qv) {
        boolQ.must(qv.toQuery());
      } else {
        boolQ.must(query);
      }
    }
    return new Query.Builder().bool(boolQ.build()).build();
  }

  public static BoolQuery.Builder boolQuery() {
    return new BoolQuery.Builder();
  }
}
