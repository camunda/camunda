/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.opensearch.client.async;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.reindexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.time;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.opensearch.client.AbstractOpenSearchOperationIT;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import java.util.List;
import org.junit.Test;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.beans.factory.annotation.Autowired;

public class OpenSearchAsyncIndexOperationsIT extends AbstractOpenSearchOperationIT {
  @Autowired UserIndex userIndex;

  @Autowired OperateProperties operateProperties;

  @Test
  public void shouldReindex() throws Exception {
    // given
    String id = "1";
    opensearchTestDataHelper.addUser(id, "test", "test");

    // when
    String dstIndex = indexPrefix + this.getClass().getSimpleName().toLowerCase();
    Query query = stringTerms("userId", List.of(id));
    var deleteByQueryRequestBuilder =
        reindexRequestBuilder(userIndex.getFullQualifiedName(), query, dstIndex)
            .waitForCompletion(false)
            .scroll(time(OpenSearchDocumentOperations.INTERNAL_SCROLL_KEEP_ALIVE_MS))
            .slices((long) operateProperties.getOpensearch().getNumberOfShards())
            .conflicts(Conflicts.Proceed)
            .refresh(true);

    var task =
        richOpenSearchClient
            .async()
            .index()
            .reindex(deleteByQueryRequestBuilder, Throwable::getMessage)
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

    var user =
        richOpenSearchClient
            .doc()
            .searchUnique(searchRequestBuilder(dstIndex).query(query), UserEntity.class, id);
    assertThat(user.getDisplayName()).isEqualTo("test");
  }
}
