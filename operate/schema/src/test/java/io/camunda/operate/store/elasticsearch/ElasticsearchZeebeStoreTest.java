/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

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
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.client.IndicesClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticsearchZeebeStoreTest {

  @Mock private RestHighLevelClient zeebeEsClient;
  @Mock private IndicesClient indicesClient;
  @Spy private OperateProperties operateProperties = new OperateProperties();

  @InjectMocks private ElasticsearchZeebeStore zeebeStore;

  @BeforeEach
  void setUp() {
    operateProperties.getZeebeElasticsearch().setPrefix("my-zeebe");
  }

  @Test
  void shouldResolvePrefixFromZeebeElasticsearchProperties() {
    assertThat(zeebeStore.getZeebeIndexPrefix()).isEqualTo("my-zeebe");
  }

  @Test
  void shouldThrowWhenRefreshMatchesNoIndices() throws Exception {
    final RefreshResponse response = refreshResponse(0, 0);
    when(zeebeEsClient.indices()).thenReturn(indicesClient);
    when(indicesClient.refresh(any(RefreshRequest.class), any(RequestOptions.class)))
        .thenReturn(response);

    assertThatThrownBy(() -> zeebeStore.refreshIndex("zeebe-record*process-instance*"))
        .isInstanceOf(OperateRuntimeException.class)
        .hasMessageContaining("zeebe-record*process-instance*");
  }

  @Test
  void shouldThrowWhenRefreshHasFailedShards() throws Exception {
    final RefreshResponse response = refreshResponse(3, 1);
    when(zeebeEsClient.indices()).thenReturn(indicesClient);
    when(indicesClient.refresh(any(RefreshRequest.class), any(RequestOptions.class)))
        .thenReturn(response);

    assertThatThrownBy(() -> zeebeStore.refreshIndex("my-zeebe*process-instance*"))
        .isInstanceOf(OperateRuntimeException.class)
        .hasMessageContaining("my-zeebe*process-instance*");
  }

  @Test
  void shouldThrowWhenRefreshRequestFails() throws Exception {
    when(zeebeEsClient.indices()).thenReturn(indicesClient);
    when(indicesClient.refresh(any(RefreshRequest.class), any(RequestOptions.class)))
        .thenThrow(new IOException("cluster unreachable"));

    assertThatThrownBy(() -> zeebeStore.refreshIndex("my-zeebe*process-instance*"))
        .isInstanceOf(OperateRuntimeException.class);
  }

  @Test
  void shouldNotThrowWhenRefreshMatchesHealthyIndices() throws Exception {
    final RefreshResponse response = refreshResponse(3, 0);
    when(zeebeEsClient.indices()).thenReturn(indicesClient);
    when(indicesClient.refresh(any(RefreshRequest.class), any(RequestOptions.class)))
        .thenReturn(response);

    assertThatCode(() -> zeebeStore.refreshIndex("my-zeebe*process-instance*"))
        .doesNotThrowAnyException();
  }

  private RefreshResponse refreshResponse(final int totalShards, final int failedShards) {
    final RefreshResponse response = mock(RefreshResponse.class);
    when(response.getTotalShards()).thenReturn(totalShards);
    // unused once totalShards == 0, since refreshIndex throws before checking failed shards
    lenient().when(response.getFailedShards()).thenReturn(failedShards);
    return response;
  }
}
