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
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

@ExtendWith(MockitoExtension.class)
class OpensearchUtilTest {
  private static final String INDEX_NAME = "list-view-index";
  private static final String ALIAS_NAME = "list-view-alias";
  private static final String DOC_ID = "123";

  @Mock private TemplateDescriptor templateDescr;
  @Mock private RichOpenSearchClient osClient;

  @Mock private OpenSearchDocumentOperations docOperations;

  @Mock private SearchResponse searchResponse;
  @Captor private ArgumentCaptor<SearchRequest.Builder> searchRequestCaptor;

  @BeforeEach
  void setup() {
    lenient().when(templateDescr.getFullQualifiedName()).thenReturn(INDEX_NAME);
    lenient().when(templateDescr.getAlias()).thenReturn(ALIAS_NAME);
    when(osClient.doc()).thenReturn(docOperations);
  }

  @Test
  void shouldReturnSourceWhenCallingGetByIdAndGetResultExists() {
    when(docOperations.getWithRetries(any(), any(), any(), any()))
        .thenReturn(Optional.of(Map.of("treePath", "/a/b/c")));

    assertThat(
            OpensearchUtil.getByIdOrSearchArchives(
                osClient, templateDescr, DOC_ID, QueryType.ONLY_RUNTIME, "treePath"))
        .isEqualTo(Map.of("treePath", "/a/b/c"));

    verifyGetRequest();
    verifyNoMoreInteractions(docOperations);
  }

  @Test
  void shouldUseCorrectRoutingWhenCallingGetById() {
    assertThat(
            OpensearchUtil.getByIdOrSearchArchives(
                osClient, templateDescr, DOC_ID, "routing", QueryType.ONLY_RUNTIME, "treePath"))
        .isNull();

    verifyGetRequest("routing");
    verifyNoMoreInteractions(docOperations);
  }

  @Test
  void shouldReturnNullWhenCallingGetByIdAndGetResultDoesNotExists() {
    assertThat(
            OpensearchUtil.getByIdOrSearchArchives(
                osClient, templateDescr, DOC_ID, QueryType.ONLY_RUNTIME, "treePath"))
        .isNull();

    verifyGetRequest();
    verifyNoMoreInteractions(docOperations);
  }

  @Test
  void shouldFallbackOnSearchWhenCallingGetByIdAndGetRequestFindsNothingAndReadingArchive() {

    givenSearchReturns(Map.of("treePath", "/search/a/b/c"));

    assertThat(
            OpensearchUtil.getByIdOrSearchArchives(
                osClient, templateDescr, DOC_ID, QueryType.ALL, "treePath"))
        .isEqualTo(Map.of("treePath", "/search/a/b/c"));

    verifyGetRequest();
    verifySearchRequest();
  }

  @Test
  void shouldReturnNullWhenCallingGetByIdAndSearchFallbackAlsoReturnsNothing() {
    givenSearchReturns();

    assertThat(
            OpensearchUtil.getByIdOrSearchArchives(
                osClient, templateDescr, DOC_ID, QueryType.ALL, "treePath"))
        .isNull();

    verifyGetRequest();
    verifySearchRequest();
  }

  private void verifyGetRequest() {
    verifyGetRequest(DOC_ID);
  }

  private void verifyGetRequest(final String routing) {
    verify(docOperations).getWithRetries(INDEX_NAME, DOC_ID, routing, Map.class);
  }

  private void verifySearchRequest() {
    verify(docOperations).search(searchRequestCaptor.capture(), any());

    final SearchRequest.Builder searchRequestBuilder = searchRequestCaptor.getValue();
    final SearchRequest searchRequest = searchRequestBuilder.build();
    assertThat(searchRequest.index()).isEqualTo(List.of(ALIAS_NAME));
    assertThat(json(searchRequest))
        .isEqualTo(
            """
            {"_source":{"includes":["treePath"]},"query":{"ids":{"values":["123"]}}}""");
  }

  private void givenSearchReturns(final Map... docs) {
    when(docOperations.search(any(), any())).thenReturn(searchResponse);
    when(searchResponse.documents()).thenReturn(Arrays.asList(docs));
  }

  private String json(final JsonpSerializable serializable) {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final JsonbJsonpMapper mapper = new JsonbJsonpMapper();
    final JsonGenerator generator = mapper.jsonProvider().createGenerator(baos);
    serializable.serialize(generator, mapper);
    generator.close();
    return baos.toString();
  }
}
