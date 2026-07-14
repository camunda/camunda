/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.search;

import io.camunda.search.clients.core.SearchBulkIndexRequest;
import io.camunda.search.clients.core.SearchBulkResponse;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.os.transformers.OpensearchTransformer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.List;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;

/**
 * Maps a raw bulk response onto the neutral per-item outcomes, paired positionally with the request
 * that produced it (bulk responses report items in request order). Mirrors the single-document
 * {@code index()} semantics item-wise: a version conflict (status 409) on an item that opted into
 * external versioning is a fenced write — {@link SearchBulkResponse.ItemResult#NOOP} — while any
 * other item error is a genuine {@link SearchBulkResponse.ItemResult#FAILED}.
 */
public class SearchBulkResponseTransformer<T>
    extends OpensearchTransformer<
        Tuple<SearchBulkIndexRequest<T>, BulkResponse>, SearchBulkResponse> {

  private static final int STATUS_CONFLICT = 409;

  public SearchBulkResponseTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SearchBulkResponse apply(final Tuple<SearchBulkIndexRequest<T>, BulkResponse> value) {
    final List<SearchIndexRequest<T>> requested = value.getLeft().items();
    final List<BulkResponseItem> reported = value.getRight().items();
    if (reported.size() != requested.size()) {
      throw new IllegalStateException(
          "Expected one bulk response item per request item, but got "
              + reported.size()
              + " items for "
              + requested.size()
              + " requests");
    }
    final List<SearchBulkResponse.Item> items = new ArrayList<>(reported.size());
    for (int i = 0; i < reported.size(); i++) {
      items.add(toItem(requested.get(i), reported.get(i)));
    }
    return new SearchBulkResponse(items);
  }

  private static SearchBulkResponse.Item toItem(
      final SearchIndexRequest<?> requested, final BulkResponseItem reported) {
    final SearchBulkResponse.ItemResult result = resultOf(requested, reported);
    final String error =
        result == SearchBulkResponse.ItemResult.FAILED ? describe(reported.error()) : null;
    return new SearchBulkResponse.Item(requested.id(), requested.index(), result, error);
  }

  private static SearchBulkResponse.ItemResult resultOf(
      final SearchIndexRequest<?> requested, final BulkResponseItem reported) {
    if (reported.error() == null) {
      return SearchBulkResponse.ItemResult.APPLIED;
    }
    if (requested.versionType() != null && reported.status() == STATUS_CONFLICT) {
      // The item opted into external versioning, and the store holds an equal-or-newer
      // version: the write was fenced, which is an outcome to report, not an error to raise.
      return SearchBulkResponse.ItemResult.NOOP;
    }
    return SearchBulkResponse.ItemResult.FAILED;
  }

  private static String describe(final ErrorCause error) {
    return error.reason() != null ? error.type() + ": " + error.reason() : error.type();
  }
}
