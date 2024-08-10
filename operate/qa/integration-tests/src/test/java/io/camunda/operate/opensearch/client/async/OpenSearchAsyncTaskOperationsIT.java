/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.opensearch.client.async;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.opensearch.client.AbstractOpenSearchOperationIT;
import org.assertj.core.api.ThrowableAssert;
import org.junit.Test;

public class OpenSearchAsyncTaskOperationsIT extends AbstractOpenSearchOperationIT {
  @Test
  public void totalImpactedByTaskShouldHandleMissingTask() throws Exception {
    // given
    final var node = richOpenSearchClient.cluster().nodesStats().keySet().iterator().next();
    final var missingTaskId = node + ":" + Long.MAX_VALUE;

    // when
    final ThrowableAssert.ThrowingCallable throwingCallable =
        () ->
            withThreadPoolTaskScheduler(
                scheduler -> {
                  try {
                    return richOpenSearchClient
                        .async()
                        .task()
                        .totalImpactedByTask(missingTaskId, scheduler)
                        .get();
                  } catch (Exception e) {
                    if (e.getCause() instanceof OperateRuntimeException) {
                      throw (OperateRuntimeException) e.getCause();
                    }
                    throw new RuntimeException(e);
                  }
                });

    // then
    assertThatThrownBy(throwingCallable).isInstanceOf(OperateRuntimeException.class);
  }
}
