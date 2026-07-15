/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CollectionUtilTest {

  @Test
  void shouldReturnFalseWhenElementsAreUnique() {
    assertThat(CollectionUtil.containsDuplicates(List.of("a", "b", "c"))).isFalse();
  }

  @Test
  void shouldReturnFalseForAnEmptyCollection() {
    assertThat(CollectionUtil.containsDuplicates(List.of())).isFalse();
  }

  @Test
  void shouldReturnTrueWhenElementsAreDuplicated() {
    assertThat(CollectionUtil.containsDuplicates(List.of("a", "b", "a"))).isTrue();
  }

  @Test
  void shouldReturnFalseWhenProjectedValuesAreUnique() {
    assertThat(
            CollectionUtil.containsDuplicates(
                List.of(new Item("a", 1), new Item("b", 1)), Item::name))
        .isFalse();
  }

  @Test
  void shouldReturnTrueWhenProjectedValuesAreDuplicated() {
    assertThat(
            CollectionUtil.containsDuplicates(
                List.of(new Item("a", 1), new Item("a", 2)), Item::name))
        .isTrue();
  }

  private record Item(String name, int id) {}
}
