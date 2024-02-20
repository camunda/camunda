/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
    var node = richOpenSearchClient.cluster().nodesStats().keySet().iterator().next();
    var missingTaskId = node + ":" + Long.MAX_VALUE;

    // when
    ThrowableAssert.ThrowingCallable throwingCallable =
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
