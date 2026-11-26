/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.security.core.authz.ResourceAccessChecks;
import org.junit.jupiter.api.Test;

class CaseDbReaderTest {

  private final CaseDbReader reader = new CaseDbReader();

  @Test
  void shouldRejectSearchUntilImplemented() {
    // given
    final var query = SearchQueryBuilders.caseSearchQuery().build();

    // when / then
    assertThatThrownBy(() -> reader.search(query, ResourceAccessChecks.disabled()))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("66836");
  }

  @Test
  void shouldRejectGetByKeyUntilImplemented() {
    // when / then
    assertThatThrownBy(() -> reader.getByKey(1L, ResourceAccessChecks.disabled()))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("66836");
  }
}
