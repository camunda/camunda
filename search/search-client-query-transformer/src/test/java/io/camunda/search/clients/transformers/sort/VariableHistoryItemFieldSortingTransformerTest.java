/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOrder;
import io.camunda.search.sort.VariableHistoryItemSort;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate;
import org.junit.jupiter.api.Test;

class VariableHistoryItemFieldSortingTransformerTest extends AbstractSortTransformerTest {

  @Test
  void shouldSortByVariableHistoryItemKey() {
    // given
    final var request =
        SearchQueryBuilders.variableHistoryItemSearchQuery(
            q -> q.sort(s -> s.variableHistoryItemKey().desc()));

    // when
    final var sort = transformRequest(request);

    // then
    assertThat(sort).hasSize(2);
    assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field())
                  .isEqualTo(VariableHistoryTemplate.VARIABLE_HISTORY_ITEM_KEY);
              assertThat(t.field().order()).isEqualTo(SortOrder.DESC);
            });
  }

  @Test
  void shouldSortByVariableHistoryItemKeyAscendingWhenNoSortIsGiven() {
    // given
    final var request = SearchQueryBuilders.variableHistoryItemSearchQuery(q -> q);

    // when
    final var sort = transformRequest(request);

    // then
    assertThat(sort).hasSize(1);
    assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field())
                  .isEqualTo(VariableHistoryTemplate.VARIABLE_HISTORY_ITEM_KEY);
              assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
  }

  @Test
  void shouldRejectAnUnknownSortField() {
    // given
    final var transformer =
        ServiceTransformers.newInstance(new IndexDescriptors("", true))
            .getFieldSortingTransformer(VariableHistoryItemSort.class);

    // when / then
    assertThatThrownBy(() -> transformer.apply("unknownField"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknownField");
  }
}
