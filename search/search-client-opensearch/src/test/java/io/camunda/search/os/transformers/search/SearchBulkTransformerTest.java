/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.search.clients.core.SearchBulkIndexRequest;
import io.camunda.search.clients.core.SearchBulkResponse;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.VersionType;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.OperationType;

public class SearchBulkTransformerTest {

  private final OpensearchTransformers transformers = new OpensearchTransformers();
  private SearchTransfomer<SearchBulkIndexRequest<TestDocument>, BulkRequest> requestTransformer;
  private SearchTransfomer<
          Tuple<SearchBulkIndexRequest<TestDocument>, BulkResponse>, SearchBulkResponse>
      responseTransformer;

  @BeforeEach
  public void before() {
    requestTransformer = transformers.getTransformer(SearchBulkIndexRequest.class);
    responseTransformer = transformers.getTransformer(SearchBulkResponse.class);
  }

  @Test
  public void shouldCreateOneIndexOperationPerItem() {
    // given one versioned and one unversioned item
    final var versionedDoc = new TestDocument("versioned");
    final var plainDoc = new TestDocument("plain");
    final SearchBulkIndexRequest<TestDocument> bulkRequest =
        new SearchBulkIndexRequest<>(
            List.of(
                SearchIndexRequest.of(
                    b ->
                        b.id("foo")
                            .index("bar")
                            .routing("foobar")
                            .document(versionedDoc)
                            .version(42L)
                            .versionType(SearchIndexRequest.VersionType.EXTERNAL_GTE)),
                SearchIndexRequest.of(b -> b.id("baz").index("bar").document(plainDoc))));

    // when
    final BulkRequest result = requestTransformer.apply(bulkRequest);

    // then every item became an index operation carrying its own fields
    assertThat(result.operations()).hasSize(2);
    final var first = result.operations().get(0).index();
    assertThat(first.id()).isEqualTo("foo");
    assertThat(first.index()).isEqualTo("bar");
    assertThat(first.routing()).isEqualTo("foobar");
    assertThat(first.document()).isEqualTo(versionedDoc);
    assertThat(first.version()).isEqualTo(42L);
    assertThat(first.versionType()).isEqualTo(VersionType.ExternalGte);
    final var second = result.operations().get(1).index();
    assertThat(second.id()).isEqualTo("baz");
    assertThat(second.version()).isNull();
    assertThat(second.versionType()).isNull();
  }

  @Test
  public void shouldMapItemOutcomesMirroringTheSingleDocumentSemantics() {
    // given a bulk of three versioned items and one unversioned item
    final SearchBulkIndexRequest<TestDocument> bulkRequest =
        new SearchBulkIndexRequest<>(
            List.of(
                versionedItem("applied"),
                versionedItem("fenced"),
                versionedItem("broken"),
                SearchIndexRequest.of(
                    b -> b.id("plain").index("bar").document(new TestDocument("plain")))));
    // and a raw response where they applied, hit the version fence, failed on a mapping error,
    // and hit a conflict without having opted into versioning
    final BulkResponse rawResponse =
        BulkResponse.of(
            b ->
                b.errors(true)
                    .took(1)
                    .items(
                        List.of(
                            rawItem("applied", 200, null, null),
                            rawItem(
                                "fenced",
                                409,
                                "version_conflict_engine_exception",
                                "current version is higher"),
                            rawItem("broken", 400, "mapper_parsing_exception", "bad field"),
                            rawItem("plain", 409, "version_conflict_engine_exception", "boom"))));

    // when
    final SearchBulkResponse result = responseTransformer.apply(Tuple.of(bulkRequest, rawResponse));

    // then only the versioned conflict is a fenced NOOP; other errors surface as failures
    assertThat(result.items())
        .extracting(
            SearchBulkResponse.Item::id,
            SearchBulkResponse.Item::index,
            SearchBulkResponse.Item::result,
            SearchBulkResponse.Item::error)
        .containsExactly(
            tuple("applied", "bar", SearchBulkResponse.ItemResult.APPLIED, null),
            tuple("fenced", "bar", SearchBulkResponse.ItemResult.NOOP, null),
            tuple(
                "broken",
                "bar",
                SearchBulkResponse.ItemResult.FAILED,
                "mapper_parsing_exception: bad field"),
            tuple(
                "plain",
                "bar",
                SearchBulkResponse.ItemResult.FAILED,
                "version_conflict_engine_exception: boom"));
  }

  @Test
  public void shouldFailWhenResponseItemsDoNotMatchRequestItems() {
    // given a raw response with fewer items than the request
    final SearchBulkIndexRequest<TestDocument> bulkRequest =
        new SearchBulkIndexRequest<>(List.of(versionedItem("a"), versionedItem("b")));
    final BulkResponse rawResponse =
        BulkResponse.of(b -> b.errors(false).took(1).items(List.of(rawItem("a", 200, null, null))));

    // when / then
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> responseTransformer.apply(Tuple.of(bulkRequest, rawResponse)));
  }

  @Test
  public void shouldFailToBuildAnEmptyBulkRequest() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new SearchBulkIndexRequest<TestDocument>(List.of()));
  }

  private static SearchIndexRequest<TestDocument> versionedItem(final String id) {
    return SearchIndexRequest.of(
        b ->
            b.id(id)
                .index("bar")
                .document(new TestDocument(id))
                .version(7L)
                .versionType(SearchIndexRequest.VersionType.EXTERNAL_GTE));
  }

  private static BulkResponseItem rawItem(
      final String id, final int status, final String errorType, final String errorReason) {
    return BulkResponseItem.of(
        b -> {
          b.operationType(OperationType.Index).id(id).index("bar").status(status);
          if (errorType != null) {
            b.error(e -> e.type(errorType).reason(errorReason));
          }
          return b;
        });
  }

  record TestDocument(String id) {}
}
