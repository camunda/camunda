/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.opensearch.client.async;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.deleteByQueryRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.opensearch.client.AbstractOpenSearchOperationIT;
import io.camunda.operate.property.OperateProperties;
import io.camunda.webapps.schema.descriptors.index.OperateUserIndex;
import java.util.List;
import org.junit.Test;
import org.opensearch.client.opensearch._types.Conflicts;
import org.springframework.beans.factory.annotation.Autowired;

public class OpenSearchAsyncDocumentOperationsIT extends AbstractOpenSearchOperationIT {
  @Autowired OperateUserIndex operateUserIndex;

  @Autowired OperateProperties operateProperties;

  @Test
  public void shouldDelete() throws Exception {
    // given
    opensearchTestDataHelper.addUser("1", "test", "test");

    // when
    final var deleteByQueryRequestBuilder =
        deleteByQueryRequestBuilder(operateUserIndex.getFullQualifiedName())
            .query(stringTerms("userId", List.of("1")))
            .waitForCompletion(false)
            .slices((long) operateProperties.getOpensearch().getNumberOfShards())
            .conflicts(Conflicts.Proceed);

    final var task =
        richOpenSearchClient
            .async()
            .doc()
            .delete(deleteByQueryRequestBuilder, Throwable::getMessage)
            .get()
            .task();

    // then
    final var total =
        withThreadPoolTaskScheduler(
            scheduler -> {
              try {
                return richOpenSearchClient
                    .async()
                    .task()
                    .totalImpactedByTask(task, scheduler)
                    .get();
              } catch (final Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertThat(total).isEqualTo(1);
  }
}
