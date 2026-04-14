/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.io.IOException;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticsearchUtilTest {
  private static final String INDEX_NAME = "list-view-index";
  private static final String ALIAS_NAME = "list-view-alias";

  @Mock private IndexTemplateDescriptor templateDescr;
  @Mock private RestHighLevelClient esClient;
  @Mock private SearchResponse searchResponse;
  @Mock private SearchHit searchHit;

  @BeforeEach
  void setup() {
    lenient().when(templateDescr.getFullQualifiedName()).thenReturn(INDEX_NAME);
    lenient().when(templateDescr.getAlias()).thenReturn(ALIAS_NAME);
  }

  @Test
  void shouldClearScrollWhenProcessorThrowsInScrollWith() throws IOException {
    final SearchHits nonEmptyHits =
        new SearchHits(
            new SearchHit[] {searchHit}, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
    when(searchResponse.getHits()).thenReturn(nonEmptyHits);
    when(searchResponse.getScrollId()).thenReturn("scroll-id-1");
    when(esClient.search(any(SearchRequest.class), any())).thenReturn(searchResponse);

    final SearchRequest request = new SearchRequest(ALIAS_NAME);
    assertThatThrownBy(
            () ->
                ElasticsearchUtil.scrollWith(
                    request,
                    esClient,
                    hits -> {
                      throw new RuntimeException("processor failure");
                    },
                    null,
                    null))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("processor failure");

    final ArgumentCaptor<ClearScrollRequest> clearCaptor =
        ArgumentCaptor.forClass(ClearScrollRequest.class);
    verify(esClient).clearScroll(clearCaptor.capture(), any());
    assertThat(clearCaptor.getValue().getScrollIds()).containsExactly("scroll-id-1");
  }

  @Test
  void shouldClearScrollWhenScrollCallThrowsInScroll() throws IOException {
    final SearchHits nonEmptyHits =
        new SearchHits(
            new SearchHit[] {searchHit}, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
    when(searchResponse.getHits()).thenReturn(nonEmptyHits);
    when(searchResponse.getScrollId()).thenReturn("scroll-id-2");
    when(searchHit.getSourceAsString()).thenReturn("{}");
    when(esClient.search(any(SearchRequest.class), any())).thenReturn(searchResponse);
    when(esClient.scroll(any(SearchScrollRequest.class), any()))
        .thenThrow(new IOException("network boom"));

    final SearchRequest request = new SearchRequest(ALIAS_NAME);
    assertThatThrownBy(
            () ->
                ElasticsearchUtil.scroll(
                    request, Object.class, new ObjectMapper(), esClient, null, null))
        .isInstanceOf(IOException.class)
        .hasMessage("network boom");

    final ArgumentCaptor<ClearScrollRequest> clearCaptor =
        ArgumentCaptor.forClass(ClearScrollRequest.class);
    verify(esClient).clearScroll(clearCaptor.capture(), any());
    assertThat(clearCaptor.getValue().getScrollIds()).containsExactly("scroll-id-2");
  }
}
