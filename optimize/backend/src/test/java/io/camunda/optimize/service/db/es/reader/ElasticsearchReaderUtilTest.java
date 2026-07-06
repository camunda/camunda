/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SearchType;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElasticsearchReaderUtilTest {

  @Test
  void rebuildShouldPreserveAllConfiguredFields() {
    // given
    final SearchRequest original =
        SearchRequest.of(
            b ->
                b.index("test-index-1", "test-index-2")
                    .query(Query.of(q -> q.matchAll(m -> m)))
                    .size(25)
                    .from(10)
                    .sort(s -> s.field(f -> f.field("timestamp").order(SortOrder.Desc)))
                    .searchType(SearchType.QueryThenFetch)
                    .scroll(s -> s.time("60s"))
                    .trackTotalHits(t -> t.enabled(true))
                    .allowNoIndices(true)
                    .ignoreUnavailable(true)
                    .expandWildcards(ExpandWildcard.Open)
                    .timeout("30s")
                    .terminateAfter(1000L)
                    .routing("routing-value")
                    .preference("_local")
                    .requestCache(true)
                    .explain(false)
                    .version(true)
                    .seqNoPrimaryTerm(true)
                    .trackScores(false)
                    .searchAfter(List.of(FieldValue.of("sort")))
                    .storedFields("_source")
                    .stats("group1"));

    // when
    final SearchRequest rebuilt = ElasticsearchReaderUtil.rebuild(original).build();

    // then
    assertThat(rebuilt.index()).isEqualTo(original.index());
    assertThat(rebuilt.query()).isEqualTo(original.query());
    assertThat(rebuilt.size()).isEqualTo(original.size());
    assertThat(rebuilt.from()).isEqualTo(original.from());
    assertThat(rebuilt.sort()).isEqualTo(original.sort());
    assertThat(rebuilt.searchType()).isEqualTo(original.searchType());
    assertThat(rebuilt.scroll()).isEqualTo(original.scroll());
    assertThat(rebuilt.trackTotalHits()).isEqualTo(original.trackTotalHits());
    assertThat(rebuilt.allowNoIndices()).isEqualTo(original.allowNoIndices());
    assertThat(rebuilt.ignoreUnavailable()).isEqualTo(original.ignoreUnavailable());
    assertThat(rebuilt.expandWildcards()).isEqualTo(original.expandWildcards());
    assertThat(rebuilt.timeout()).isEqualTo(original.timeout());
    assertThat(rebuilt.terminateAfter()).isEqualTo(original.terminateAfter());
    assertThat(rebuilt.routing()).isEqualTo(original.routing());
    assertThat(rebuilt.preference()).isEqualTo(original.preference());
    assertThat(rebuilt.requestCache()).isEqualTo(original.requestCache());
    assertThat(rebuilt.explain()).isEqualTo(original.explain());
    assertThat(rebuilt.version()).isEqualTo(original.version());
    assertThat(rebuilt.seqNoPrimaryTerm()).isEqualTo(original.seqNoPrimaryTerm());
    assertThat(rebuilt.trackScores()).isEqualTo(original.trackScores());
    assertThat(rebuilt.searchAfter()).isEqualTo(original.searchAfter());
    assertThat(rebuilt.storedFields()).isEqualTo(original.storedFields());
    assertThat(rebuilt.stats()).isEqualTo(original.stats());
  }

  @Test
  void rebuildShouldHandleMinimalRequest() {
    // given
    final SearchRequest original = SearchRequest.of(b -> b.index("minimal-index"));

    // when
    final SearchRequest rebuilt = ElasticsearchReaderUtil.rebuild(original).build();

    // then
    assertThat(rebuilt.index()).isEqualTo(List.of("minimal-index"));
    assertThat(rebuilt.query()).isNull();
    assertThat(rebuilt.size()).isNull();
    assertThat(rebuilt.from()).isNull();
    assertThat(rebuilt.sort()).isEmpty();
  }

  @Test
  void rebuildShouldAllowModificationOfRebuiltRequest() {
    // given
    final SearchRequest original =
        SearchRequest.of(
            b ->
                b.index("test-index")
                    .query(Query.of(q -> q.matchAll(m -> m)))
                    .size(10)
                    .sort(s -> s.field(f -> f.field("id").order(SortOrder.Asc))));

    // when — rebuild and override size
    final SearchRequest modified = ElasticsearchReaderUtil.rebuild(original).size(50).build();

    // then — size is overridden, other fields preserved
    assertThat(modified.size()).isEqualTo(50);
    assertThat(modified.index()).isEqualTo(original.index());
    assertThat(modified.query()).isEqualTo(original.query());
    assertThat(modified.sort()).isEqualTo(original.sort());
  }
}
