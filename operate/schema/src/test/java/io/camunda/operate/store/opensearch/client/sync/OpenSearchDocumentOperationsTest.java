/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import net.jodah.failsafe.function.CheckedSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesClient;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.opensearch.client.util.ObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class OpenSearchDocumentOperationsTest {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpenSearchDocumentOperationsTest.class);
  private static final String INDEX_NAME = "operate-list-view-8.3.0_";
  private static final String DOC_ID = "123";
  private static final String ROUTING = "456";

  @Mock private OpenSearchClient openSearchClient;
  @Mock private OpenSearchIndicesClient indicesClient;
  @Mock private GetResponse<Map> getResponse;

  @Captor private ArgumentCaptor<GetRequest> getRequestCaptor;

  @Captor
  private ArgumentCaptor<Function<ExistsRequest.Builder, ObjectBuilder<ExistsRequest>>>
      existsRequestCaptor;

  private OpenSearchDocumentOperations documentOperations;

  @BeforeEach
  void setUp() {
    documentOperations = spy(new OpenSearchDocumentOperations(LOGGER, openSearchClient));
    // the executeWithRetries would retry a failing operation for a 10min that is too long for
    // tests. This simply override to execute the supplier once and return response
    doAnswer(invocation -> ((CheckedSupplier<?>) invocation.getArgument(0)).get())
        .when(documentOperations)
        .executeWithRetries(any(CheckedSupplier.class));
  }

  @Test
  void shouldReturnSourceWhenDocumentIsFound() throws IOException {
    when(getResponse.found()).thenReturn(true);
    when(getResponse.source()).thenReturn(Map.of("treePath", "/a/b/c"));
    when(openSearchClient.get(any(GetRequest.class), eq(Map.class))).thenReturn(getResponse);

    assertThat(documentOperations.getWithRetries(INDEX_NAME, DOC_ID, ROUTING, Map.class))
        .contains(Map.of("treePath", "/a/b/c"));

    verify(openSearchClient).get(getRequestCaptor.capture(), eq(Map.class));
    final GetRequest getRequest = getRequestCaptor.getValue();
    assertThat(getRequest.index()).isEqualTo(INDEX_NAME);
    assertThat(getRequest.id()).isEqualTo(DOC_ID);
    assertThat(getRequest.routing()).isEqualTo(ROUTING);
    verify(openSearchClient, never()).indices();
  }

  @Test
  void shouldReturnEmptyWhenDocumentIsNotFound() throws IOException {
    // this is scenario how it should work
    when(getResponse.found()).thenReturn(false);
    when(openSearchClient.get(any(GetRequest.class), eq(Map.class))).thenReturn(getResponse);

    assertThat(documentOperations.getWithRetries(INDEX_NAME, DOC_ID, ROUTING, Map.class)).isEmpty();

    verify(openSearchClient, never()).indices();
  }

  @Test
  void shouldReturnEmptyWhenGetFailsWith404AndIndexExists() throws IOException {
    // current opensearch bug where document not found is treated as index not found,
    // see https://github.com/opensearch-project/opensearch-java/issues/424
    givenGetFailsWith(documentNotFoundException());
    givenIndexExists(true);

    assertThat(documentOperations.getWithRetries(INDEX_NAME, DOC_ID, ROUTING, Map.class)).isEmpty();

    verify(indicesClient).exists(existsRequestCaptor.capture());
    assertThat(existsRequestCaptor.getValue().apply(new ExistsRequest.Builder()).build().index())
        .containsExactly(INDEX_NAME);
  }

  @Test
  void shouldRethrowWhenGetFailsWith404AndIndexDoesNotExist() throws IOException {
    final OpenSearchException exception = indexNotFoundException();
    givenGetFailsWith(exception);
    givenIndexExists(false);

    assertThatThrownBy(
            () -> documentOperations.getWithRetries(INDEX_NAME, DOC_ID, ROUTING, Map.class))
        .isSameAs(exception);

    verify(indicesClient).exists(existsRequestCaptor.capture());
  }

  @Test
  void shouldRethrowWithoutCheckingTheIndexWhenGetFailsWithOtherStatus() throws IOException {
    final OpenSearchException exception =
        openSearchException(503, "cluster_block_exception", "index read-only");
    givenGetFailsWith(exception);

    assertThatThrownBy(
            () -> documentOperations.getWithRetries(INDEX_NAME, DOC_ID, ROUTING, Map.class))
        .isSameAs(exception);

    verify(openSearchClient, never()).indices();
  }

  @Test
  void shouldReturnEmptyWhenGetWithoutRoutingFailsWith404AndIndexExists() throws IOException {
    // current opensearch bug where document not found is treated as index not found,
    // see https://github.com/opensearch-project/opensearch-java/issues/424
    givenGetFailsWith(documentNotFoundException());
    givenIndexExists(true);

    assertThat(documentOperations.getWithRetries(INDEX_NAME, DOC_ID, Map.class)).isEmpty();

    verify(indicesClient).exists(existsRequestCaptor.capture());
    assertThat(existsRequestCaptor.getValue().apply(new ExistsRequest.Builder()).build().index())
        .containsExactly(INDEX_NAME);
  }

  @Test
  void shouldRethrowWhenGetWithoutRoutingFailsWith404AndIndexDoesNotExist() throws IOException {
    final OpenSearchException exception = indexNotFoundException();
    givenGetFailsWith(exception);
    givenIndexExists(false);

    assertThatThrownBy(() -> documentOperations.getWithRetries(INDEX_NAME, DOC_ID, Map.class))
        .isSameAs(exception);

    verify(indicesClient).exists(existsRequestCaptor.capture());
  }

  private void givenGetFailsWith(final OpenSearchException exception) throws IOException {
    when(openSearchClient.get(any(GetRequest.class), eq(Map.class))).thenThrow(exception);
  }

  private void givenIndexExists(final boolean exists) throws IOException {
    when(openSearchClient.indices()).thenReturn(indicesClient);
    when(indicesClient.exists(any(Function.class))).thenReturn(new BooleanResponse(exists));
  }

  private OpenSearchException documentNotFoundException() {
    // the AWS transport reports a missing document as a generic 404 rather than a typed error,
    // producing the message "Request failed: [http_exception] server returned 404"
    return openSearchException(404, "http_exception", "server returned 404");
  }

  private OpenSearchException indexNotFoundException() {
    return openSearchException(
        404, "index_not_found_exception", "no such index [" + INDEX_NAME + "]");
  }

  private OpenSearchException openSearchException(
      final int status, final String type, final String reason) {
    return new OpenSearchException(
        ErrorResponse.of(
            builder -> builder.status(status).error(error -> error.type(type).reason(reason))));
  }
}
