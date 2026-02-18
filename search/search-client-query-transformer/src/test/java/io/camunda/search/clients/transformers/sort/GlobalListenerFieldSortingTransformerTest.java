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

import org.junit.jupiter.api.Test;

class GlobalListenerFieldSortingTransformerTest {

  private final GlobalListenerFieldSortingTransformer transformer =
      new GlobalListenerFieldSortingTransformer();

  @Test
  void shouldReturnIdForIdField() {
    assertThat(transformer.apply("id")).isEqualTo("id");
  }

  @Test
  void shouldReturnListenerIdForListenerIdField() {
    assertThat(transformer.apply("listenerId")).isEqualTo("listenerId");
  }

  @Test
  void shouldReturnTypeForTypeField() {
    assertThat(transformer.apply("type")).isEqualTo("type");
  }

  @Test
  void shouldReturnAfterNonGlobalForAfterNonGlobalField() {
    assertThat(transformer.apply("afterNonGlobal")).isEqualTo("afterNonGlobal");
  }

  @Test
  void shouldReturnPriorityForPriorityField() {
    assertThat(transformer.apply("priority")).isEqualTo("priority");
  }

  @Test
  void shouldReturnSourceForSourceField() {
    assertThat(transformer.apply("source")).isEqualTo("source");
  }

  @Test
  void shouldReturnListenerTypeForListenerTypeField() {
    assertThat(transformer.apply("listenerType")).isEqualTo("listenerType");
  }

  @Test
  void shouldThrowExceptionForUnknownField() {
    assertThatThrownBy(() -> transformer.apply("unknownField"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown sortField: unknownField");
  }

  @Test
  void shouldReturnIdAsDefaultSortField() {
    assertThat(transformer.defaultSortField()).isEqualTo("id");
  }
}
