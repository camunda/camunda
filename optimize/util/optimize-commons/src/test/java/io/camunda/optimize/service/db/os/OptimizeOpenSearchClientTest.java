/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.OperationType;

class OptimizeOpenSearchClientTest {

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
}
