/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch._types.OpenSearchException;

class ErrorTypeTest {

  // ============ enum value tests ============

  @Test
  void shouldExposeExpectedMetricValues() {
    assertThat(ErrorType.TOO_MANY_BUCKETS.getValue()).isEqualTo("too_many_buckets");
    assertThat(ErrorType.INDEX_NOT_FOUND.getValue()).isEqualTo("index_not_found");
    assertThat(ErrorType.VERSION_CONFLICT.getValue()).isEqualTo("version_conflict");
    assertThat(ErrorType.SEARCH_CONTEXT_MISSING.getValue()).isEqualTo("search_context_missing");
    assertThat(ErrorType.NESTED_LIMIT_EXCEEDED.getValue()).isEqualTo("nested_limit_exceeded");
    assertThat(ErrorType.ELASTICSEARCH_ERROR.getValue()).isEqualTo("elasticsearch_error");
    assertThat(ErrorType.OPENSEARCH_ERROR.getValue()).isEqualTo("opensearch_error");
  }

  // ============ fromException() Tests ============

  @Test
  void shouldReturnNullForNullException() {
    // when
    final ErrorType errorType = ErrorType.fromException(null);

    // then
    assertThat(errorType).isNull();
  }

  @Test
  void shouldReturnNullForUnknownException() {
    // given
    final Throwable exception = new NullPointerException("something is null");

    // when
    final ErrorType errorType = ErrorType.fromException(exception);

    // then
    assertThat(errorType).isNull();
  }

  @Test
  void shouldHandleExceptionWithNullMessage() {
    // given
    final Throwable exception = new OptimizeRuntimeException((String) null);

    // when
    final ErrorType errorType = ErrorType.fromException(exception);

    // then
    // Should return null
    assertThat(errorType).isNull();
  }

  @Test
  void shouldDetectVersionConflictFromExceptionMessage() {
    // given
    final Throwable exception = new RuntimeException("version_conflict_engine_exception");

    // when
    final ErrorType errorType = ErrorType.fromException(exception);

    // then
    assertThat(errorType).isEqualTo(ErrorType.VERSION_CONFLICT);
  }

  @Test
  void shouldDetectIndexNotFoundFromExceptionMessage() {
    // given
    final Throwable exception = new RuntimeException("index_not_found_exception");

    // when
    final ErrorType errorType = ErrorType.fromException(exception);

    // then
    assertThat(errorType).isEqualTo(ErrorType.INDEX_NOT_FOUND);
  }

  @Test
  void shouldDetectNestedLimitExceededFromExceptionMessage() {
    // given
    final Throwable exception =
        new RuntimeException("The number of nested documents has exceeded the allowed limit");

    // when
    final ErrorType errorType = ErrorType.fromException(exception);

    // then
    assertThat(errorType).isEqualTo(ErrorType.NESTED_LIMIT_EXCEEDED);
  }

  @Test
  void shouldTraverseCauseChainAndDetectNestedError() {
    // given
    final Throwable rootCause = new RuntimeException("index_not_found_exception");
    final Throwable wrappedException = new RuntimeException("wrapper error", rootCause);

    // when
    final ErrorType errorType = ErrorType.fromException(wrappedException);

    // then
    assertThat(errorType).isEqualTo(ErrorType.INDEX_NOT_FOUND);
  }

  @Test
  void shouldReturnNullForUnknownRuntimeException() {
    // given
    final Throwable exception = new NullPointerException("something is null");

    // when
    final ErrorType errorType = ErrorType.fromException(exception);

    // then
    assertThat(errorType).isNull();
  }

  @Test
  void shouldReturnNullForOptimizeRuntimeExceptionWithoutKnownPattern() {
    // given
    final Throwable exception = new OptimizeRuntimeException("Something went wrong");

    // when
    final ErrorType errorType = ErrorType.fromException(exception);

    // then
    assertThat(errorType).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "VERSION_CONFLICT",
        "version_conflict",
        "Version_Conflict",
        "contains version_conflict here"
      })
  void shouldDetectVersionConflictCaseInsensitively(final String message) {
    // given
    final Throwable exception = new RuntimeException(message);

    // when
    final ErrorType errorType = ErrorType.fromException(exception);

    // then
    assertThat(errorType).isEqualTo(ErrorType.VERSION_CONFLICT);
  }

  @Test
  void shouldDetectNestedLimitWhenKeywordsAppearInDifferentCauseMessages() {
    // given
    final Throwable root = new RuntimeException("nested_limit_exceeded documents");
    final Throwable wrapped = new RuntimeException("elasticsearch error", root);

    // when
    final ErrorType errorType = ErrorType.fromException(wrapped);

    // then
    assertThat(errorType).isEqualTo(ErrorType.NESTED_LIMIT_EXCEEDED);
  }

  @Test
  void shouldReturnElasticsearchErrorForUnhandledElasticsearchException() {
    // given
    final ElasticsearchException exception =
        new ElasticsearchException(
            "some_elasticsearch_failure",
            ErrorResponse.of(
                e ->
                    e.error(ErrorCause.of(c -> c.reason("generic elasticsearch problem")))
                        .status(400)));

    // when
    final ErrorType errorType = ErrorType.fromException(exception);

    // then
    assertThat(errorType).isEqualTo(ErrorType.ELASTICSEARCH_ERROR);
  }

  @Test
  void shouldReturnOpenSearchErrorForUnhandledOpenSearchException() {
    // given
    final OpenSearchException exception =
        new OpenSearchException(
            new org.opensearch.client.opensearch._types.ErrorResponse.Builder()
                .status(500)
                .error(
                    new org.opensearch.client.opensearch._types.ErrorCause.Builder()
                        .type("some_opensearch_failure")
                        .reason("generic opensearch problem")
                        .build())
                .build());

    // when
    final ErrorType errorType = ErrorType.fromException(exception);

    // then
    assertThat(errorType).isEqualTo(ErrorType.OPENSEARCH_ERROR);
  }

  // ============ wrapped ES/OS exception tests (cause-chain fallback) ============

  @Test
  void shouldReturnElasticsearchErrorWhenWrappedByOptimizeRuntimeException() {
    // given - OptimizeRuntimeException wraps a generic ElasticsearchException (no pattern match)
    final ElasticsearchException esException =
        new ElasticsearchException(
            "generic_elasticsearch_failure",
            ErrorResponse.of(
                e -> e.error(ErrorCause.of(c -> c.reason("some es error"))).status(500)));
    final Throwable wrapped = new OptimizeRuntimeException("wrapper", esException);

    // when
    final ErrorType errorType = ErrorType.fromException(wrapped);

    // then
    assertThat(errorType).isEqualTo(ErrorType.ELASTICSEARCH_ERROR);
  }

  @Test
  void shouldReturnOpenSearchErrorWhenWrappedByOptimizeRuntimeException() {
    // given - OptimizeRuntimeException wraps a generic OpenSearchException (no pattern match)
    final OpenSearchException osException =
        new OpenSearchException(
            new org.opensearch.client.opensearch._types.ErrorResponse.Builder()
                .status(500)
                .error(
                    new org.opensearch.client.opensearch._types.ErrorCause.Builder()
                        .type("generic_opensearch_failure")
                        .reason("some os error")
                        .build())
                .build());
    final Throwable wrapped = new OptimizeRuntimeException("wrapper", osException);

    // when
    final ErrorType errorType = ErrorType.fromException(wrapped);

    // then
    assertThat(errorType).isEqualTo(ErrorType.OPENSEARCH_ERROR);
  }

  @Test
  void shouldPreferSpecificTypeOverElasticsearchFallbackWhenWrapperMessageMatches() {
    // given - wrapper has the specific message pattern; cause is a generic ElasticsearchException
    final ElasticsearchException esException =
        new ElasticsearchException(
            "generic_elasticsearch_failure",
            ErrorResponse.of(
                e -> e.error(ErrorCause.of(c -> c.reason("some es error"))).status(409)));
    final Throwable wrapped =
        new OptimizeRuntimeException("version_conflict_engine_exception", esException);

    // when
    final ErrorType errorType = ErrorType.fromException(wrapped);

    // then - specific type wins over the generic ELASTICSEARCH_ERROR fallback
    assertThat(errorType).isEqualTo(ErrorType.VERSION_CONFLICT);
  }

  @Test
  void shouldPreferSpecificTypeOverElasticsearchFallbackWhenCauseMessageMatches() {
    // given - wrapper is generic; the ElasticsearchException itself has the specific message
    final ElasticsearchException esException =
        new ElasticsearchException(
            "index_not_found_exception",
            ErrorResponse.of(
                e -> e.error(ErrorCause.of(c -> c.reason("index not found"))).status(404)));
    final Throwable wrapped = new OptimizeRuntimeException("wrapper error", esException);

    // when
    final ErrorType errorType = ErrorType.fromException(wrapped);

    // then - specific type wins even when found deeper in the chain
    assertThat(errorType).isEqualTo(ErrorType.INDEX_NOT_FOUND);
  }

  @Test
  void shouldPreferSpecificVersionConflictOverElasticsearchFallback() {
    // given
    final ElasticsearchException exception =
        new ElasticsearchException(
            "version_conflict_engine_exception",
            ErrorResponse.of(
                e ->
                    e.error(ErrorCause.of(c -> c.reason("version conflict happened")))
                        .status(409)));

    // when
    final ErrorType errorType = ErrorType.fromException(exception);

    // then
    assertThat(errorType).isEqualTo(ErrorType.VERSION_CONFLICT);
  }

  @Test
  void shouldPreferSpecificIndexNotFoundOverOpenSearchFallback() {
    // given
    final OpenSearchException exception =
        new OpenSearchException(
            new org.opensearch.client.opensearch._types.ErrorResponse.Builder()
                .status(404)
                .error(
                    new org.opensearch.client.opensearch._types.ErrorCause.Builder()
                        .type("index_not_found_exception")
                        .reason("index not found")
                        .build())
                .build());

    // when
    final ErrorType errorType = ErrorType.fromException(exception);

    // then
    assertThat(errorType).isEqualTo(ErrorType.INDEX_NOT_FOUND);
  }
}
