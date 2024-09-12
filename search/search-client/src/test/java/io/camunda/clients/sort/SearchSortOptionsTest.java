/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.clients.sort;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.service.search.sort.SortOptionsBuilders;
import org.junit.jupiter.api.Test;

public class SearchSortOptionsTest {

  @Test
  public void shouldThrowOnEmptySearchRequest() {
    // given

    // when - then throw
    assertThatThrownBy(() -> SortOptionsBuilders.field().build())
        .hasMessageContaining("Expected field name for field sorting, but got null.")
        .isInstanceOf(NullPointerException.class);
  }
}
