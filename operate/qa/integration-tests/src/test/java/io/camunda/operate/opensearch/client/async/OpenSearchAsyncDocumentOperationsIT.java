/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.opensearch.client.async;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.deleteByQueryRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.opensearch.client.AbstractOpenSearchOperationIT;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.UserIndex;
import java.util.List;
import org.junit.Test;
import org.opensearch.client.opensearch._types.Conflicts;
import org.springframework.beans.factory.annotation.Autowired;

public class OpenSearchAsyncDocumentOperationsIT extends AbstractOpenSearchOperationIT {
  @Autowired UserIndex userIndex;

  @Autowired OperateProperties operateProperties;

  @Test
  public void shouldDelete() throws Exception {
    // given
    opensearchTestDataHelper.addUser("1", "test", "test");

    // when
    var deleteByQueryRequestBuilder =
        deleteByQueryRequestBuilder(userIndex.getFullQualifiedName())
            .query(stringTerms("userId", List.of("1")))
            .waitForCompletion(false)
            .slices((long) operateProperties.getOpensearch().getNumberOfShards())
            .conflicts(Conflicts.Proceed);

    var task =
        richOpenSearchClient
            .async()
            .doc()
            .delete(deleteByQueryRequestBuilder, Throwable::getMessage)
            .get()
            .task();

    // then
    var total =
        withThreadPoolTaskScheduler(
            scheduler -> {
              try {
                return richOpenSearchClient
                    .async()
                    .task()
                    .totalImpactedByTask(task, scheduler)
                    .get();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertThat(total).isEqualTo(1);
  }
}
