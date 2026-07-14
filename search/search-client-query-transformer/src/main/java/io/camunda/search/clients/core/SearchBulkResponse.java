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
 * The per-item outcomes of a {@link SearchBulkIndexRequest}, positionally aligned with the
 * request's items. Every item carries its own {@link ItemResult}; the bulk as a whole only fails
 * (with an exception from the client) when the request could not be executed at all — a transport
 * or request-level error.
 */
public record SearchBulkResponse(List<Item> items) {

  public SearchBulkResponse {
    Objects.requireNonNull(items, "Expected bulk response items, but given items were null.");
    items = List.copyOf(items);
  }

  /**
   * One item's outcome.
   *
   * @param error the store's error description — non-null exactly when {@link #result} is {@link
   *     ItemResult#FAILED}
   */
  public record Item(String id, String index, ItemResult result, String error) {

    public Item {
      Objects.requireNonNull(result, "Expected a bulk item result, but given result was null.");
    }
  }

  /** How the store disposed of one bulk item. */
  public enum ItemResult {
    /** The document was written (created or updated). */
    APPLIED,
    /**
     * The item opted into external versioning and the store already holds an equal-or-newer
     * version: the write was fenced — an outcome to report, not an error (mirrors the
     * single-document {@link SearchWriteResponse.Result#NOOP}).
     */
    NOOP,
    /** The item genuinely failed (e.g. a mapping error); {@link Item#error()} names the cause. */
    FAILED
  }
}
