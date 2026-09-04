/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ShardFailure;
import org.opensearch.client.opensearch._types.ShardStatistics;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesClient;
import org.opensearch.client.opensearch.indices.RefreshResponse;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class OpensearchZeebeStoreTest {

  @Mock private OpenSearchClient openSearchClient;
  @Mock private OpenSearchIndicesClient indicesClient;
  @Spy private OperateProperties operateProperties = new OperateProperties();

  @InjectMocks private OpensearchZeebeStore zeebeStore;

  @BeforeEach
  void setUp() {
    operateProperties.getZeebeOpensearch().setPrefix("my-zeebe-os");
  }

  @Test
  void shouldResolvePrefixFromZeebeOpensearchProperties() {
    assertThat(zeebeStore.getZeebeIndexPrefix()).isEqualTo("my-zeebe-os");
  }

  @Test
  void shouldThrowWhenRefreshMatchesNoIndices() throws Exception {
    final RefreshResponse response = refreshResponse(0, List.of());
    when(openSearchClient.indices()).thenReturn(indicesClient);
    when(indicesClient.refresh(any(Function.class))).thenReturn(response);

    assertThatThrownBy(() -> zeebeStore.refreshIndex("zeebe-record*process-instance*"))
        .isInstanceOf(OperateRuntimeException.class)
        .hasMessageContaining("zeebe-record*process-instance*");
  }

  @Test
  void shouldThrowWhenRefreshHasShardFailures() throws Exception {
    final RefreshResponse response = refreshResponse(3, List.of(mock(ShardFailure.class)));
    when(openSearchClient.indices()).thenReturn(indicesClient);
    when(indicesClient.refresh(any(Function.class))).thenReturn(response);

    assertThatThrownBy(() -> zeebeStore.refreshIndex("my-zeebe-os*process-instance*"))
        .isInstanceOf(OperateRuntimeException.class)
        .hasMessageContaining("my-zeebe-os*process-instance*");
  }

  @Test
  void shouldThrowWhenRefreshRequestFails() throws Exception {
    when(openSearchClient.indices()).thenReturn(indicesClient);
    when(indicesClient.refresh(any(Function.class)))
        .thenThrow(new IOException("cluster unreachable"));

    assertThatThrownBy(() -> zeebeStore.refreshIndex("my-zeebe-os*process-instance*"))
        .isInstanceOf(OperateRuntimeException.class);
  }

  @Test
  void shouldNotThrowWhenRefreshMatchesHealthyIndices() throws Exception {
    final RefreshResponse response = refreshResponse(3, List.of());
    when(openSearchClient.indices()).thenReturn(indicesClient);
    when(indicesClient.refresh(any(Function.class))).thenReturn(response);

    assertThatCode(() -> zeebeStore.refreshIndex("my-zeebe-os*process-instance*"))
        .doesNotThrowAnyException();
  }

  private RefreshResponse refreshResponse(
      final int totalShards, final List<ShardFailure> failures) {
    final RefreshResponse response = mock(RefreshResponse.class);
    final ShardStatistics shards = mock(ShardStatistics.class);
    when(shards.total()).thenReturn(totalShards);
    // unused when totalShards == 0, since refreshIndex throws before checking failures
    lenient().when(shards.failures()).thenReturn(failures);
    when(response.shards()).thenReturn(shards);
    return response;
  }
}
