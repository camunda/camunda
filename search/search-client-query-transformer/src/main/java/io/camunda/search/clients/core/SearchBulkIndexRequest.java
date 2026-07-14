/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import java.util.List;
import java.util.Objects;

/**
 * A neutral bulk of single-document index (upsert) requests, executed as one {@code _bulk} call.
 * Each item is a complete {@link SearchIndexRequest} — index, id, routing, document, and the
 * optional external version pair — and the store applies items in list order, so multiple writes to
 * the same id within one bulk keep last-writer-wins semantics.
 *
 * <p>Items succeed or fail <em>independently</em>: a rejected or failed item never affects its
 * siblings and never fails the request as a whole. Per-item outcomes come back positionally in the
 * {@link SearchBulkResponse}, with exactly the single-document {@code index()} semantics per item —
 * a version conflict on an item that opted into versioning is a {@link
 * SearchBulkResponse.ItemResult#NOOP} outcome, not an error.
 */
public record SearchBulkIndexRequest<T>(List<SearchIndexRequest<T>> items) {

  public SearchBulkIndexRequest {
    Objects.requireNonNull(items, "Expected bulk items, but given items were null.");
    if (items.isEmpty()) {
      throw new IllegalArgumentException("Expected bulk items, but given items were empty.");
    }
    items = List.copyOf(items);
  }
}
