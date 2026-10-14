/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.OperationType;

@ExtendWith(MockitoExtension.class)
class OptimizeOpenSearchClientTest {
  @Mock private OpenSearchAsyncClient openSearchAsyncClient;

  @InjectMocks private OptimizeOpenSearchClient optimizeOpenSearchClient;

  @Test
  void shouldDescribeWhyBulkItemsFailed() {
    // given
    final BulkResponseItem blocked =
        BulkResponseItem.of(
            item ->
                item.operationType(OperationType.Update)
                    .index("optimize-process-instance-a_v8")
                    .id("1")
                    .status(403)
                    .error(
                        e ->
                            e.type("cluster_block_exception")
                                .reason("index [optimize-process-instance-a_v8] blocked")));
    final BulkResponseItem succeeded =
        BulkResponseItem.of(
            item ->
                item.operationType(OperationType.Update)
                    .index("optimize-process-instance-a_v8")
                    .id("2")
                    .status(200));
    final BulkResponse bulkResponse =
        BulkResponse.of(b -> b.errors(true).took(1L).items(blocked, succeeded));

    // when
    final String description = OptimizeOpenSearchClient.describeFailedItems(bulkResponse);

    // then
    assertThat(description)
        .isEqualTo("Update cluster_block_exception index [optimize-process-instance-a_v8] blocked");
  }

  @Test
  void shouldNameWhyBulkItemsFailedWhenSkippingDocumentsAtNestedLimit() {
    // given
    final OptimizeOpenSearchClient client = spy(optimizeOpenSearchClient);
    final BulkResponseItem blocked =
        BulkResponseItem.of(
            item ->
                item.operationType(OperationType.Update)
                    .index("optimize-process-instance-a_v8")
                    .id("1")
                    .status(403)
                    .error(
                        e ->
                            e.type("cluster_block_exception")
                                .reason("index [optimize-process-instance-a_v8] blocked")));
    doReturn(BulkResponse.of(b -> b.errors(true).took(1L).items(blocked)))
        .when(client)
        .bulk(any(), any());
    final BulkOperation operation =
        BulkOperation.of(
            o ->
                o.update(
                    u -> u.index("optimize-process-instance-a_v8").id("1").document(Map.of())));

    // when / then
    assertThatThrownBy(
            () ->
                client.doBulkRequest(
                    BulkRequest.Builder::new, List.of(operation), "process instances", true))
        .isInstanceOf(OptimizeRuntimeException.class)
        .hasMessageContaining("cluster_block_exception");
  }
}
