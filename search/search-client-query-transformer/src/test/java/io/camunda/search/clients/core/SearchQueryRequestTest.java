/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class SearchQueryRequestTest {

  @Test
  public void shouldThrowOnEmptySearchRequest() {
    // given

    // when - then throw
    assertThatThrownBy(() -> SearchQueryRequest.of(b -> b))
        .hasMessageContaining("Expected to create request for index, but given index was null.")
        .isInstanceOf(NullPointerException.class);
  }
}
