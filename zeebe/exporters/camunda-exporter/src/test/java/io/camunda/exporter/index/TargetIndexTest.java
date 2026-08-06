/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.index;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TargetIndexTest {

  @Test
  void shouldCreateMainIndex() {
    final var index = TargetIndex.mainIndex("index-name");
    assertThat(index.name()).isEqualTo("index-name");
    assertThat(index.getIndexFamily()).isEqualTo(index);
  }

  @Test
  void shouldCreateOrdinalIndex() {
    final var index = TargetIndex.ordinalIndex("index-name", 12);
    assertThat(index.name()).isEqualTo("index-nameord00012");
    assertThat(index.getIndexFamily()).isEqualTo(new IndexFamilyImpl("index-name"));
  }

  @Test
  void shouldReturnIndexFamilyFromNameForMainIndex() {
    assertThat(TargetIndex.getIndexFamilyFromName("index-name1").name()).isEqualTo("index-name1");
  }

  @Test
  void shouldReturnIndexFamilyFromNameForOrdinalIndex() {
    assertThat(TargetIndex.getIndexFamilyFromName("index-name2ord00012").name())
        .isEqualTo("index-name2");
  }
}
