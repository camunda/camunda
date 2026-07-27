/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CollectionUtilTest {

  private record Item(String name, int id) {}

  @Nested
  class Duplicates {
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
  }

  @Nested
  class Zip {
    @Test
    void shouldZipCollectionsOfEqualSize() {
      assertThat(CollectionUtil.zipAsStream(List.of("a", "b"), List.of(1, 2)))
          .containsExactly(new Tuple<>("a", 1), new Tuple<>("b", 2));
    }

    @Test
    void shouldZipStoppingAtShorterLeftCollection() {
      assertThat(CollectionUtil.zipAsStream(List.of("a"), List.of(1, 2)))
          .containsExactly(new Tuple<>("a", 1));
    }

    @Test
    void shouldZipStoppingAtShorterRightCollection() {
      assertThat(CollectionUtil.zipAsStream(List.of("a", "b"), List.of(1)))
          .containsExactly(new Tuple<>("a", 1));
    }

    @Test
    void shouldZipReturnEmptyWhenEitherCollectionIsEmpty() {
      assertThat(CollectionUtil.zipAsStream(List.of(), List.of(1, 2))).isEmpty();
      assertThat(CollectionUtil.zipAsStream(List.of("a", "b"), List.of())).isEmpty();
    }
  }
}
