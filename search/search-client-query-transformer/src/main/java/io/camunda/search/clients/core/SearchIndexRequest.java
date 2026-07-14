/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import static io.camunda.search.clients.core.RequestBuilders.indexRequest;

import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

/**
 * A neutral single-document index (upsert) request.
 *
 * @param version an optional external document version; requires {@link #versionType}. When set,
 *     the store applies the write only if the version passes the version-type's predicate against
 *     the stored document's version — a rejected (conflicting) write is reported as {@link
 *     SearchWriteResponse.Result#NOOP}, not an error, so writers can fence stale writes without
 *     read-modify-write round trips.
 * @param versionType how {@link #version} is compared against the stored version; {@code null} (the
 *     default) keeps the store's internal versioning and never rejects.
 */
public record SearchIndexRequest<T>(
    String id, String index, String routing, T document, Long version, VersionType versionType) {

  public static <T> SearchIndexRequest<T> of(
      final Function<SearchIndexRequest.Builder<T>, ObjectBuilder<SearchIndexRequest<T>>> fn) {
    return indexRequest(fn);
  }

  /** External version comparison semantics, mirroring the document stores' version types. */
  public enum VersionType {
    /** Apply only when the request version is strictly greater than the stored one. */
    EXTERNAL,
    /** Apply when the request version is greater than or equal to the stored one. */
    EXTERNAL_GTE
  }

  public static final class Builder<T> implements ObjectBuilder<SearchIndexRequest<T>> {

    private String id;
    private String index;
    private String routing;
    private T document;
    private Long version;
    private VersionType versionType;

    public Builder<T> id(final String value) {
      id = value;
      return this;
    }

    public Builder<T> index(final String value) {
      index = value;
      return this;
    }

    public Builder<T> routing(final String value) {
      routing = value;
      return this;
    }

    public Builder<T> document(final T value) {
      document = value;
      return this;
    }

    public Builder<T> version(final Long value) {
      version = value;
      return this;
    }

    public Builder<T> versionType(final VersionType value) {
      versionType = value;
      return this;
    }

    @Override
    public SearchIndexRequest<T> build() {
      if ((version == null) != (versionType == null)) {
        throw new IllegalArgumentException(
            "Expected version and versionType to be set together, but got version="
                + version
                + ", versionType="
                + versionType);
      }
      return new SearchIndexRequest<T>(
          id,
          Objects.requireNonNull(
              index, "Expected to create request for index, but given index was null."),
          routing,
          Objects.requireNonNull(
              document, "Expected to index a document, but given document was null."),
          version,
          versionType);
    }
  }
}
