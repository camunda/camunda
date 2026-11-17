/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.search.query.VariableNameQuery;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VariableDocumentReaderTest {

  @Test
  void shouldReturnEmptyListWithoutQueryingWhenNoProcessDefinitionKeyGiven() {
    // given
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var indexDescriptor = mock(IndexDescriptor.class);
    final var reader = new VariableDocumentReader(executor, indexDescriptor);

    // when
    final var names =
        reader.searchVariableNames(VariableNameQuery.of(b -> b), ResourceAccessChecks.disabled());

    // then
    assertThat(names).isEmpty();
    verifyNoInteractions(executor);
  }

  @ParameterizedTest
  @ValueSource(strings = {"after", "before"})
  void shouldRejectUndecodablePaginationCursor(final String direction) {
    // given a cursor that is valid base64 but not a decodable pagination cursor
    final var cursor = "bm90IGEgcmVhbCBjdXJzb3IgcGF5bG9hZA==";
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var reader = new VariableDocumentReader(executor, mock(IndexDescriptor.class));
    final var query =
        VariableNameQuery.of(
            b ->
                b.filter(f -> f.processDefinitionKeys(1L))
                    .page(p -> "after".equals(direction) ? p.after(cursor) : p.before(cursor)));

    // when / then
    assertThatThrownBy(() -> reader.searchVariableNames(query, ResourceAccessChecks.disabled()))
        .isInstanceOfSatisfying(
            CamundaSearchException.class,
            e -> assertThat(e.getReason()).isEqualTo(Reason.INVALID_ARGUMENT));
    verifyNoInteractions(executor);
  }
}
