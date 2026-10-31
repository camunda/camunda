/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.UpdateByQueryRequest;
import co.elastic.clients.elasticsearch.core.UpdateByQueryResponse;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskRepositoryESTest {

  private OptimizeElasticsearchClient esClient;
  private ConfigurationService configurationService;
  private TaskRepositoryES taskRepositoryES;

  @BeforeEach
  void init() {
    esClient = mock(OptimizeElasticsearchClient.class);
    lenient().when(esClient.addPrefixesToIndices(any())).thenReturn(List.of("test-index"));
    configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
    configurationService
        .getElasticSearchConfiguration()
        .getConnection()
        .setClusterTaskCheckingEnabled(false);
    taskRepositoryES = new TaskRepositoryES(esClient, configurationService);
  }

  @Test
  void shouldKeepDefaultScrollSizeForUpdateWhenNoneIsRequested() throws IOException {
    // given
    final ArgumentCaptor<UpdateByQueryRequest> requestCaptor =
        ArgumentCaptor.forClass(UpdateByQueryRequest.class);
    when(esClient.submitUpdateTask(requestCaptor.capture()))
        .thenReturn(UpdateByQueryResponse.of(b -> b.updated(1L).timedOut(false)));

    // when
    taskRepositoryES.tryUpdateByQueryRequest(
        "test reports", mock(Script.class), Query.of(q -> q.matchAll(m -> m)), "test-index");

    // then -- unrelated callers keep the Elasticsearch default batch size
    assertThat(requestCaptor.getValue().scrollSize()).isNull();
  }

  @Test
  void shouldApplyRequestedScrollSizeForUpdate() throws IOException {
    // given
    final ArgumentCaptor<UpdateByQueryRequest> requestCaptor =
        ArgumentCaptor.forClass(UpdateByQueryRequest.class);
    when(esClient.submitUpdateTask(requestCaptor.capture()))
        .thenReturn(UpdateByQueryResponse.of(b -> b.updated(1L).timedOut(false)));

    // when
    taskRepositoryES.tryUpdateByQueryRequest(
        "test definitions",
        mock(Script.class),
        Query.of(q -> q.matchAll(m -> m)),
        50,
        "test-index");

    // then
    assertThat(requestCaptor.getValue().scrollSize()).isEqualTo(50L);
  }
}
