/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TargetIndexTest {

  @Test
  void shouldCreateMainIndex() {
    final var index = TargetIndex.mainIndex("index-name");
    assertThat(index.name()).isEqualTo("index-name");
    assertThat(index.getIndexFamily()).isEqualTo(index);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldFailToCreateMainIndexWithEmptyName(final String name) {
    assertThatThrownBy(() -> TargetIndex.mainIndex(name))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Main index name must not be null or blank");
  }

  @Test
  void shouldCreateOrdinalIndex() {
    final var index = TargetIndex.ordinalIndex("index-name", 12);
    assertThat(index.name()).isEqualTo("index-nameord00012");
    assertThat(index.getIndexFamily()).isEqualTo(new IndexFamilyImpl("index-name"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldFailToCreateOrdinalIndexWithEmptyPrefix(final String prefix) {
    assertThatThrownBy(() -> TargetIndex.ordinalIndex(prefix, 12))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Ordinal index prefix must not be null or blank");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -5})
  void shouldFailToCreateOrdinalIndexWithInvalidOrdinal(final int ordinal) {
    assertThatThrownBy(() -> TargetIndex.ordinalIndex("index-name", ordinal))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Ordinal must be greater than 0");
  }

  @Test
  void shouldReturnIndexFamilyFromNameForMainIndex() {
    assertThat(TargetIndex.getIndexFamilyFromName("index-name1").name()).isEqualTo("index-name1");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "index-name2ord00012",
        "index-name2ord00001",
        "index-name2ord00099",
        "index-name2ord999999"
      })
  void shouldReturnIndexFamilyFromNameForOrdinalIndex(final String indexName) {
    assertThat(TargetIndex.getIndexFamilyFromName(indexName).name()).isEqualTo("index-name2");
  }
}
