/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.RequestOptionsProvider;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.rest.RestStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class OptimizeElasticsearchClientTest {
  @Mock(answer = Answers.RETURNS_DEEP_STUBS, strictness = Mock.Strictness.LENIENT)
  private RestHighLevelClient highLevelRestClient;
  @Mock
  private OptimizeIndexNameService indexNameService;

  private OptimizeElasticsearchClient underTest;

  @Test
  public void indexDeleteIsRetriedOnPendingSnapshot() throws IOException, InterruptedException {
    // given
    RequestOptionsProvider requestOptionsProvider = new RequestOptionsProvider();
    underTest = new OptimizeElasticsearchClient(highLevelRestClient, indexNameService, requestOptionsProvider);
    underTest.setSnapshotInProgressRetryDelaySeconds(1);
    given(highLevelRestClient.indices()
            .delete(any(DeleteIndexRequest.class), eq(requestOptionsProvider.getRequestOptions())))
      .willAnswer(invocation -> {
        throw new ElasticsearchStatusException("snapshot_in_progress_exception", RestStatus.BAD_REQUEST);
      });

    // when the delete operation is executed
    final ScheduledExecutorService deleteIndexTask = Executors.newSingleThreadScheduledExecutor();
    deleteIndexTask.execute(() -> underTest.deleteIndexByRawIndexNames("index1"));

    // and hit the client which failed
    verify(highLevelRestClient.indices(), timeout(100).atLeastOnce())
      .delete(any(DeleteIndexRequest.class), eq(requestOptionsProvider.getRequestOptions()));
    // then the delete operation is not completed
    assertThat(deleteIndexTask.isTerminated()).isFalse();

    // then it is retried and completes eventually
    given(highLevelRestClient.indices()
            .delete(any(DeleteIndexRequest.class), eq(requestOptionsProvider.getRequestOptions())))
      .willReturn(null);

    verify(highLevelRestClient.indices(), timeout(2000).atLeast(2))
      .delete(any(DeleteIndexRequest.class), eq(requestOptionsProvider.getRequestOptions()));

    // then the delete operation is completed
    deleteIndexTask.shutdown();
    assertThat(deleteIndexTask.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
  }

}
