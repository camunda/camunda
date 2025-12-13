/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.query_dsl.IdsQuery;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.ReindexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpensearchUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchUtil.class);

  private OpensearchUtil() {
    // Utility class, no instantiation
  }

  public static <T> Either<Throwable, Long> handleResponse(
      final T response, final Throwable error, final String sourceIndexName) {
    final String operation = response instanceof ReindexResponse ? "reindex" : "deleteByQuery";
    if (error != null) {
      final var message =
          String.format(
              "Exception occurred, while performing operation %s on source index %s. Error: %s",
              operation, sourceIndexName, error.getMessage());
      return Either.left(new ArchiverException(message, error));
    }

    final var bulkFailures =
        response instanceof ReindexResponse
            ? ((ReindexResponse) response).failures()
            : ((DeleteByQueryResponse) response).failures();

    if (!bulkFailures.isEmpty()) {
      LOGGER.error(
          "Failures occurred when performing operation: {} on source index {}. See details below.",
          operation,
          sourceIndexName);
      bulkFailures.forEach(f -> LOGGER.error(f.toString()));
      return Either.left(new ArchiverException(String.format("Operation %s failed", operation)));
    }

    LOGGER.debug("Operation {} succeeded on source index {}", operation, sourceIndexName);
    return Either.right(
        response instanceof ReindexResponse
            ? ((ReindexResponse) response).total()
            : ((DeleteByQueryResponse) response).total());
  }

  public static <T> CompletableFuture<SearchResponse<T>> searchAsync(
      final SearchRequest request, final Class<T> tClass, final OpenSearchAsyncClient client) {
    try {
      return client.search(request, tClass);
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  public static CompletableFuture<ReindexResponse> reindexAsync(
      final ReindexRequest request, final OpenSearchAsyncClient client) {
    try {
      return client.reindex(request);
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  public static CompletableFuture<DeleteByQueryResponse> deleteAsync(
      final DeleteByQueryRequest request, final OpenSearchAsyncClient client) {
    try {
      return client.deleteByQuery(request);
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  public static Map<String, Object> getByIdOrSearchArchives(
      final RichOpenSearchClient osClient,
      final TemplateDescriptor template,
      final String id,
      final QueryType queryType,
      final String... fields) {
    return getByIdOrSearchArchives(osClient, template, id, id, queryType, fields);
  }

  public static Map<String, Object> getByIdOrSearchArchives(
      final RichOpenSearchClient osClient,
      final TemplateDescriptor template,
      final String id,
      final String routing,
      final QueryType queryType,
      final String... fields) {

    // first we'll search using a get request in the runtime index
    // as that usually should be where the doc is and a get request will avoid
    // issues with the indexes not being refreshed yet
    final Optional<Map> maybeDoc =
        osClient.doc().getWithRetries(template.getFullQualifiedName(), id, routing, Map.class);
    if (maybeDoc.isPresent()) {
      return maybeDoc.get();
    }
    if (queryType == QueryType.ALL) {
      // we only do this if we're configured to look at the archive - which is not the default
      final SourceConfig source =
          SourceConfig.of(
              builder -> builder.filter(filter -> filter.includes(Arrays.asList(fields))));
      final IdsQuery idsQuery = IdsQuery.of(builder -> builder.values(id));
      final SearchRequest.Builder searchRequestBuilder =
          new SearchRequest.Builder()
              .index(template.getAlias())
              .source(source)
              .query(q -> q.ids(idsQuery));

      final SearchResponse<Map> searchResponse =
          osClient.doc().search(searchRequestBuilder, Map.class);
      if (!searchResponse.documents().isEmpty()) {
        return searchResponse.documents().getFirst();
      }
    }
    return null;
  }
}
