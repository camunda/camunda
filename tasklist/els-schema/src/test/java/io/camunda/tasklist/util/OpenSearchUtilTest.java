/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.webapps.schema.entities.usermanagement.UserEntity;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ShardFailure;
import org.opensearch.client.opensearch._types.ShardStatistics;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesClient;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.opensearch.client.opensearch.indices.RefreshResponse;

class OpenSearchUtilTest {

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void shouldClearScrollWhenScrollCallThrowsInScroll() throws Exception {
    final OpenSearchClient osClient = mock(OpenSearchClient.class);
    final SearchResponse<UserEntity> searchResponse = mock(SearchResponse.class);
    final HitsMetadata<UserEntity> hitsMetadata = mock(HitsMetadata.class);
    final Hit<UserEntity> hit = mock(Hit.class);
    when(hit.source()).thenReturn(new UserEntity());
    when(hitsMetadata.hits()).thenReturn(List.of(hit));
    when(searchResponse.hits()).thenReturn(hitsMetadata);
    when(searchResponse.scrollId()).thenReturn("scroll-id-os");
    when(osClient.search(any(SearchRequest.class), any(Class.class))).thenReturn(searchResponse);
    when(osClient.scroll(any(ScrollRequest.class), any(Class.class)))
        .thenThrow(new IOException("network boom"));

    final SearchRequest.Builder request = new SearchRequest.Builder().index("some-alias");
    assertThatThrownBy(() -> OpenSearchUtil.scroll(request, UserEntity.class, osClient))
        .isInstanceOf(IOException.class)
        .hasMessage("network boom");

    final ArgumentCaptor<ClearScrollRequest> clearCaptor =
        ArgumentCaptor.forClass(ClearScrollRequest.class);
    verify(osClient).clearScroll(clearCaptor.capture());
    assertThat(clearCaptor.getValue().scrollId()).contains("scroll-id-os");
  }

  @Test
  void shouldThrowWhenRefreshMatchesNoIndices() throws Exception {
    final OpenSearchClient osClient = mock(OpenSearchClient.class);
    final OpenSearchIndicesClient indicesClient = mock(OpenSearchIndicesClient.class);
    final RefreshResponse refreshResponse = mock(RefreshResponse.class);
    final ShardStatistics shards = mock(ShardStatistics.class);
    when(shards.total()).thenReturn(0);
    when(refreshResponse.shards()).thenReturn(shards);
    when(osClient.indices()).thenReturn(indicesClient);
    when(indicesClient.refresh(any(RefreshRequest.class))).thenReturn(refreshResponse);

    assertThatThrownBy(
            () -> OpenSearchUtil.refreshIndicesFor(osClient, "zeebe-record*process-instance*"))
        .isInstanceOf(TasklistRuntimeException.class)
        .hasMessageContaining("zeebe-record*process-instance*");
  }

  @Test
  void shouldThrowWhenRefreshHasShardFailures() throws Exception {
    final OpenSearchClient osClient = mock(OpenSearchClient.class);
    final OpenSearchIndicesClient indicesClient = mock(OpenSearchIndicesClient.class);
    final RefreshResponse refreshResponse = mock(RefreshResponse.class);
    final ShardStatistics shards = mock(ShardStatistics.class);
    when(shards.total()).thenReturn(3);
    when(shards.failures()).thenReturn(List.of(mock(ShardFailure.class)));
    when(refreshResponse.shards()).thenReturn(shards);
    when(osClient.indices()).thenReturn(indicesClient);
    when(indicesClient.refresh(any(RefreshRequest.class))).thenReturn(refreshResponse);

    assertThatThrownBy(
            () -> OpenSearchUtil.refreshIndicesFor(osClient, "my-zeebe-os*process-instance*"))
        .isInstanceOf(TasklistRuntimeException.class)
        .hasMessageContaining("my-zeebe-os*process-instance*");
  }

  @Test
  void shouldThrowWhenRefreshRequestFails() throws Exception {
    final OpenSearchClient osClient = mock(OpenSearchClient.class);
    final OpenSearchIndicesClient indicesClient = mock(OpenSearchIndicesClient.class);
    when(osClient.indices()).thenReturn(indicesClient);
    when(indicesClient.refresh(any(RefreshRequest.class)))
        .thenThrow(new IOException("cluster unreachable"));

    assertThatThrownBy(
            () -> OpenSearchUtil.refreshIndicesFor(osClient, "my-zeebe-os*process-instance*"))
        .isInstanceOf(TasklistRuntimeException.class);
  }

  @Test
  void shouldNotThrowWhenRefreshMatchesHealthyIndices() throws Exception {
    final OpenSearchClient osClient = mock(OpenSearchClient.class);
    final OpenSearchIndicesClient indicesClient = mock(OpenSearchIndicesClient.class);
    final RefreshResponse refreshResponse = mock(RefreshResponse.class);
    final ShardStatistics shards = mock(ShardStatistics.class);
    when(shards.total()).thenReturn(3);
    when(shards.failures()).thenReturn(List.of());
    when(refreshResponse.shards()).thenReturn(shards);
    when(osClient.indices()).thenReturn(indicesClient);
    when(indicesClient.refresh(any(RefreshRequest.class))).thenReturn(refreshResponse);

    assertThatCode(
            () -> OpenSearchUtil.refreshIndicesFor(osClient, "my-zeebe-os*process-instance*"))
        .doesNotThrowAnyException();
  }
}
