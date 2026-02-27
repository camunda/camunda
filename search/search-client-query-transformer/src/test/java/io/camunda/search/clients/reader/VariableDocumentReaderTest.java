/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.search.query.SearchQueryBuilders.variableSearchQuery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.page.SearchQueryPage.SearchQueryResultType;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VariableDocumentReaderTest {

  @Test
  void shouldDeduplicateByNameAndKeepClosestScope() {
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var reader = new VariableDocumentReader(executor, mock(IndexDescriptor.class));
    final var query =
        variableSearchQuery(
                q ->
                    q.filter(f -> f.scopeKeys(List.of(1L, 2L, 3L)))
                        .sort(s -> s.name().asc())
                        .page(p -> p.from(0).size(10)));
    when(executor.search(any(), eq(io.camunda.webapps.schema.entities.VariableEntity.class), any()))
        .thenReturn(
            SearchQueryResult.of(
                result ->
                    result.items(
                        List.of(
                            new VariableEntity(
                                1L, "vehicleModel", "\"X5\"", "\"X5\"", false, 1L, 11L, 11L, "pd", "t1"),
                            new VariableEntity(
                                2L,
                                "vehicleModel",
                                "\"X5 M Competition\"",
                                "\"X5 M Competition\"",
                                false,
                                3L,
                                11L,
                                11L,
                                "pd",
                                "t1"),
                            new VariableEntity(
                                3L,
                                "instructions",
                                "\"Use official lookup\"",
                                "\"Use official lookup\"",
                                false,
                                3L,
                                11L,
                                11L,
                                "pd",
                                "t1")))));

    final var result = reader.search(query, ResourceAccessChecks.disabled());

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items())
        .extracting(VariableEntity::name)
        .containsExactly("instructions", "vehicleModel");
    assertThat(result.items().get(1).scopeKey()).isEqualTo(3L);

    final var queryCaptor = ArgumentCaptor.forClass(VariableQuery.class);
    verify(executor)
        .search(
            queryCaptor.capture(),
            eq(io.camunda.webapps.schema.entities.VariableEntity.class),
            any());
    assertThat(queryCaptor.getValue().page().resultType()).isEqualTo(SearchQueryResultType.UNLIMITED);
  }
}
