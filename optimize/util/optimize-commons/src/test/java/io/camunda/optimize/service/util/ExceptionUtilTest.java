/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import java.io.IOException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.OpenSearchException;

class ExceptionUtilTest {

  @Test
  void shouldDetectConcurrentSnapshotExecutionExceptionFromElasticsearchException() {
    // given
    final ElasticsearchException exception =
        new ElasticsearchException(
            "snapshot",
            ErrorResponse.of(
                e ->
                    e.error(
                            ErrorCause.of(
                                c ->
                                    c.type("concurrent_snapshot_execution_exception")
                                        .reason("a snapshot is already running")))
                        .status(503)));

    // when + then
    assertThat(ExceptionUtil.isConcurrentSnapshotExecutionException(exception)).isTrue();
  }

  @Test
  void shouldDetectConcurrentSnapshotExecutionExceptionFromOpenSearchException() {
    // given
    final OpenSearchException exception =
        new OpenSearchException(
            new org.opensearch.client.opensearch._types.ErrorResponse.Builder()
                .status(503)
                .error(
                    new org.opensearch.client.opensearch._types.ErrorCause.Builder()
                        .type("concurrent_snapshot_execution_exception")
                        .reason("a snapshot is already running")
                        .build())
                .build());

    // when + then
    assertThat(ExceptionUtil.isConcurrentSnapshotExecutionException(exception)).isTrue();
  }

  @Test
  void shouldDetectConcurrentSnapshotExecutionExceptionWrappedInCompletionException() {
    // given
    final OpenSearchException cause =
        new OpenSearchException(
            new org.opensearch.client.opensearch._types.ErrorResponse.Builder()
                .status(503)
                .error(
                    new org.opensearch.client.opensearch._types.ErrorCause.Builder()
                        .type("concurrent_snapshot_execution_exception")
                        .reason("a snapshot is already running")
                        .build())
                .build());
    final CompletionException wrapped = new CompletionException(cause);

    // when + then
    assertThat(ExceptionUtil.isConcurrentSnapshotExecutionException(wrapped)).isTrue();
  }

  @Test
  void shouldNotDetectConcurrentSnapshotExecutionExceptionForUnrelatedElasticsearchException() {
    // given
    final ElasticsearchException exception =
        new ElasticsearchException(
            "too_many_buckets_exception",
            ErrorResponse.of(
                e ->
                    e.error(
                            ErrorCause.of(
                                c ->
                                    c.type("too_many_buckets_exception")
                                        .reason("too many buckets")))
                        .status(400)));

    // when + then
    assertThat(ExceptionUtil.isConcurrentSnapshotExecutionException(exception)).isFalse();
  }

  @Test
  void shouldNotDetectConcurrentSnapshotExecutionExceptionForUnrelatedThrowable() {
    // given
    final IOException exception = new IOException("connection reset");

    // when + then
    assertThat(ExceptionUtil.isConcurrentSnapshotExecutionException(exception)).isFalse();
  }

  @Test
  void shouldReturnRootThrowableUnchangedWhenNotACompletionOrExecutionException() {
    // given
    final IOException exception = new IOException("connection reset");

    // when + then
    assertThat(ExceptionUtil.unwrapCompletionCause(exception)).isSameAs(exception);
  }

  @Test
  void shouldUnwrapNestedCompletionExceptions() {
    // given
    final IOException rootCause = new IOException("connection reset");
    final CompletionException wrapped = new CompletionException(new CompletionException(rootCause));

    // when + then
    assertThat(ExceptionUtil.unwrapCompletionCause(wrapped)).isSameAs(rootCause);
  }
}
