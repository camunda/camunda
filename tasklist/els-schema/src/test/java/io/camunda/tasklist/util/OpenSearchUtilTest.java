/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.exceptions.PersistenceException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;

@ExtendWith(MockitoExtension.class)
class OpenSearchUtilTest {

  @Captor private ArgumentCaptor<BulkRequest> bulkRequestCaptor;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testProcessBulkRequestRequestNotDividedWhenSizeIsLessThanOrEqualToMaxSize(
      final boolean exactSize) throws Exception {
    // given
    final OpenSearchClient osClient = mock(OpenSearchClient.class);
    final BulkResponse bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.items()).thenReturn(Collections.emptyList());
    when(osClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

    final List<BulkOperation> operations = new ArrayList<>();
    operations.add(
        BulkOperation.of(
            b ->
                b.index(
                    IndexOperation.of(
                        i ->
                            i.index("test-index")
                                .id("1")
                                .document(Collections.singletonMap("field", "value"))))));

    final BulkRequest bulkRequest = new BulkRequest.Builder().operations(operations).build();

    final long maxBulkRequestSize =
        exactSize ? 1024 : 1024 * 1024 * 90; // exact (1KB per op) or 90 MB

    // when
    OpenSearchUtil.processBulkRequest(osClient, bulkRequest, maxBulkRequestSize);

    // then
    verify(osClient, times(1)).bulk(bulkRequestCaptor.capture());
    assertThat(bulkRequestCaptor.getAllValues()).hasSize(1);
    final BulkRequest capturedRequest = bulkRequestCaptor.getValue();
    assertThat(capturedRequest.operations()).hasSize(1);
  }

  @Test
  void testProcessBulkRequestRequestIsDividedWhenSizeIsGreaterThanMaxSize() throws Exception {
    // given
    final OpenSearchClient osClient = mock(OpenSearchClient.class);
    final BulkResponse bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.items()).thenReturn(Collections.emptyList());
    when(osClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

    final List<BulkOperation> operations = new ArrayList<>();
    // Add 10 operations (estimated 10KB total)
    for (int i = 0; i < 10; i++) {
      final int index = i;
      operations.add(
          BulkOperation.of(
              b ->
                  b.index(
                      IndexOperation.of(
                          op ->
                              op.index("test-index")
                                  .id(String.valueOf(index))
                                  .document(Collections.singletonMap("field", "value" + index))))));
    }

    final BulkRequest bulkRequest = new BulkRequest.Builder().operations(operations).build();

    // Set max size to be small enough to force division (3KB)
    final long maxBulkRequestSize = 3 * 1024;

    // when
    OpenSearchUtil.processBulkRequest(osClient, bulkRequest, maxBulkRequestSize);

    // then - should be called multiple times (at least 2)
    verify(osClient, atLeast(2)).bulk(bulkRequestCaptor.capture());
    assertThat(bulkRequestCaptor.getAllValues()).hasSizeGreaterThanOrEqualTo(2);

    // Verify all operations were processed
    int totalOperationsProcessed = 0;
    for (final BulkRequest capturedRequest : bulkRequestCaptor.getAllValues()) {
      totalOperationsProcessed += capturedRequest.operations().size();
    }
    assertThat(totalOperationsProcessed).isEqualTo(10);
  }

  @Test
  void testProcessBulkRequestThrowsExceptionWhenSingleOperationExceedsMaxSize() throws Exception {
    // given
    final OpenSearchClient osClient = mock(OpenSearchClient.class);

    final List<BulkOperation> operations = new ArrayList<>();
    // The estimated size per operation is 1KB, so any max size less than that should trigger the
    // exception
    operations.add(
        BulkOperation.of(
            b ->
                b.index(
                    IndexOperation.of(
                        i ->
                            i.index("test-index")
                                .id("1")
                                .document(Collections.singletonMap("field", "value"))))));

    final BulkRequest bulkRequest = new BulkRequest.Builder().operations(operations).build();

    // Set max size to be smaller than a single operation (512 bytes)
    final long maxBulkRequestSize = 512;

    // when/then
    assertThatThrownBy(
            () -> OpenSearchUtil.processBulkRequest(osClient, bulkRequest, maxBulkRequestSize))
        .isInstanceOf(PersistenceException.class)
        .hasMessageContaining("greater than max allowed");
  }

  @Test
  void testProcessBulkRequestHandlesIOException() throws Exception {
    // given
    final OpenSearchClient osClient = mock(OpenSearchClient.class);
    when(osClient.bulk(any(BulkRequest.class))).thenThrow(new IOException("Connection error"));

    final List<BulkOperation> operations = new ArrayList<>();
    operations.add(
        BulkOperation.of(
            b ->
                b.index(
                    IndexOperation.of(
                        i ->
                            i.index("test-index")
                                .id("1")
                                .document(Collections.singletonMap("field", "value"))))));

    final BulkRequest bulkRequest = new BulkRequest.Builder().operations(operations).build();

    final long maxBulkRequestSize = 1024 * 1024 * 90; // 90 MB

    // when/then
    assertThatThrownBy(
            () -> OpenSearchUtil.processBulkRequest(osClient, bulkRequest, maxBulkRequestSize))
        .isInstanceOf(PersistenceException.class)
        .hasMessageContaining("Error when processing bulk request against OpenSearch");
  }
}
