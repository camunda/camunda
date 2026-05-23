/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.sort.SortOrder;
import org.junit.jupiter.api.Test;

public class JobSortTest extends AbstractSortTransformerTest {

  @Test
  public void shouldSortByPriorityDesc() {
    // given
    final var request = SearchQueryBuilders.jobSearchQuery(q -> q.sort(s -> s.priority().desc()));

    // when
    final var sort = transformRequest(request);

    // then
    assertThat(sort).hasSize(2); // [priority sort, default key sort]
    assertThat(sort.getFirst().field().field()).isEqualTo("priority");
    assertThat(sort.getFirst().field().order()).isEqualTo(SortOrder.DESC);
    // Pre-8.10 jobs with no stored priority sort last (ES/OS _last default)
    assertThat(sort.getFirst().field().missing()).isEqualTo("_last");
  }

  @Test
  public void shouldSortByPriorityAsc() {
    // given
    final var request = SearchQueryBuilders.jobSearchQuery(q -> q.sort(s -> s.priority().asc()));

    // when
    final var sort = transformRequest(request);

    // then
    assertThat(sort).hasSize(2);
    assertThat(sort.getFirst().field().field()).isEqualTo("priority");
    assertThat(sort.getFirst().field().order()).isEqualTo(SortOrder.ASC);
    assertThat(sort.getFirst().field().missing()).isEqualTo("_last");
  }

  @Test
  public void shouldSortByOtherFieldsWithDefaultMissingLast() {
    // given — retries is a comparable int field on jobs; it should keep the _last default
    final var request = SearchQueryBuilders.jobSearchQuery(q -> q.sort(s -> s.retries().asc()));

    // when
    final var sort = transformRequest(request);

    // then — missing is "_last" (the global default for forward pagination)
    assertThat(sort).hasSize(2);
    assertThat(sort.getFirst().field().field()).isEqualTo("retries");
    assertThat(sort.getFirst().field().missing()).isEqualTo("_last");
  }
}
