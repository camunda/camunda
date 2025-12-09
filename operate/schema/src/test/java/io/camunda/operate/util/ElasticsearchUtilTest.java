/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.util.ElasticsearchUtil.QueryType;
import java.io.IOException;
import java.util.Map;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticsearchUtilTest {
  private static final String INDEX_NAME = "list-view-index";
  private static final String ALIAS_NAME = "list-view-alias";
  private static final String DOC_ID = "123";

  @Mock private TemplateDescriptor templateDescr;
  @Mock private RestHighLevelClient esClient;

  @Mock private GetResponse getResponse;
  @Captor private ArgumentCaptor<GetRequest> getRequestCaptor;
  @Mock private SearchResponse searchResponse;
  @Mock private SearchHit searchHit;
  @Captor private ArgumentCaptor<SearchRequest> searchRequestCaptor;

  @BeforeEach
  void setup() throws IOException {
    lenient().when(templateDescr.getFullQualifiedName()).thenReturn(INDEX_NAME);
    lenient().when(templateDescr.getAlias()).thenReturn(ALIAS_NAME);
    when(esClient.get(any(), any())).thenReturn(getResponse);
  }

  @Test
  void shouldReturnSourceWhenCallingGetByIdAndGetResultExists() throws IOException {
    when(getResponse.isExists()).thenReturn(true);
    when(getResponse.getSourceAsMap()).thenReturn(Map.of("treePath", "/a/b/c"));

    assertThat(
            ElasticsearchUtil.getByIdOrSearchArchives(
                esClient, templateDescr, DOC_ID, QueryType.ONLY_RUNTIME, "treePath"))
        .isEqualTo(Map.of("treePath", "/a/b/c"));

    verifyGetRequest();
    verifyNoMoreInteractions(esClient);
  }

  @Test
  void shouldReturnNullWhenCallingGetByIdAndGetResultDoesNotExists() throws IOException {
    assertThat(
            ElasticsearchUtil.getByIdOrSearchArchives(
                esClient, templateDescr, DOC_ID, QueryType.ONLY_RUNTIME, "treePath"))
        .isNull();

    verifyGetRequest();
    verifyNoMoreInteractions(esClient);
  }

  @Test
  void shouldFallbackOnSearchWhenCallingGetByIdAndGetRequestFindsNothingAndReadingArchive()
      throws IOException {

    givenSearchReturns(searchHit);
    when(searchHit.getSourceAsMap()).thenReturn(Map.of("treePath", "/search/a/b/c"));

    assertThat(
            ElasticsearchUtil.getByIdOrSearchArchives(
                esClient, templateDescr, DOC_ID, QueryType.ALL, "treePath"))
        .isEqualTo(Map.of("treePath", "/search/a/b/c"));

    verifyGetRequest();
    verifySearchRequest();
  }

  @Test
  void shouldReturnNullWhenCallingGetByIdAndSearchFallbackAlsoReturnsNothing() throws IOException {
    givenSearchReturns();

    assertThat(
            ElasticsearchUtil.getByIdOrSearchArchives(
                esClient, templateDescr, DOC_ID, QueryType.ALL, "treePath"))
        .isNull();

    verifyGetRequest();
    verifySearchRequest();
  }

  private void verifyGetRequest() throws IOException {
    verify(esClient).get(getRequestCaptor.capture(), any());

    final GetRequest getRequest = getRequestCaptor.getValue();
    assertThat(getRequest.index()).isEqualTo(INDEX_NAME);
    assertThat(getRequest.id()).isEqualTo(DOC_ID);
  }

  private void verifySearchRequest() throws IOException {
    verify(esClient).search(searchRequestCaptor.capture(), any());

    final SearchRequest searchRequest = searchRequestCaptor.getValue();
    assertThat(searchRequest.indices()).isEqualTo(new String[] {ALIAS_NAME});
    assertThat(searchRequest.source().toString())
        .isEqualTo(
            """
            {"query":{"ids":{"values":["123"],"boost":1.0}},"_source":{"includes":["treePath"],"excludes":[]}}""");
  }

  private void givenSearchReturns(final SearchHit... hits) throws IOException {
    when(esClient.search(any(), any())).thenReturn(searchResponse);
    when(searchResponse.getHits())
        .thenReturn(
            new SearchHits(hits, new TotalHits(hits.length, TotalHits.Relation.EQUAL_TO), 1.0f));
  }
}
