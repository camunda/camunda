/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.query.VariableNameQuery;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import org.junit.jupiter.api.Test;

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
}
