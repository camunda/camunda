/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.future;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class CallbacksTest {

  @ParameterizedTest
  @ValueSource(ints = {2, 3, 6, 7, 8, 50})
  void shouldRunCallbacksInRegistrationOrder(final int count) {
    // given
    final List<Integer> order = new ArrayList<>();
    final var callbacks = callbacks(count, order);

    // when
    callbacks.run("value", null);

    // then
    assertThat(callbacks.size()).isEqualTo(count);
    assertThat(order).containsExactlyElementsOf(range(count));
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 3, 6, 7, 8, 50})
  void shouldPassValueAndErrorToCallbacks(final int count) {
    // given
    final List<String> received = new ArrayList<>();
    final var error = new IllegalStateException("failed");
    var callbacks =
        Callbacks.<String>of(
            (value, ignored) -> received.add(value + ":" + ignored),
            (value, ignored) -> received.add(value + ":" + ignored));
    for (int i = 2; i < count; i++) {
      callbacks = callbacks.add((value, ignored) -> received.add(value + ":" + ignored));
    }

    // when
    callbacks.run("value", error);

    // then
    assertThat(received).hasSize(count).containsOnly("value:" + error);
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 3, 6, 7, 8, 50})
  void shouldNotModifyCallbacksWhenAddingOne(final int count) {
    // given
    final List<Integer> order = new ArrayList<>();
    final var original = callbacks(count, order);

    // when
    final var extended = original.add((value, error) -> order.add(-1));
    original.run("value", null);

    // then the original is unchanged and the extended value has one more callback
    assertThat(original.size()).isEqualTo(count);
    assertThat(extended.size()).isEqualTo(count + 1);
    assertThat(order).containsExactlyElementsOf(range(count));
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 3, 6, 7, 8, 50})
  void shouldRunSameCallbacksAgainWithoutChange(final int count) {
    // given
    final List<Integer> order = new ArrayList<>();
    final var callbacks = callbacks(count, order);

    // when
    callbacks.run("value", null);
    callbacks.run("value", null);

    // then running reverses nothing in place, both runs are in the same order
    final var expected = new ArrayList<>(range(count));
    expected.addAll(range(count));
    assertThat(order).containsExactlyElementsOf(expected);
  }

  private static Callbacks<String> callbacks(final int count, final List<Integer> order) {
    var callbacks = Callbacks.<String>of(recording(0, order), recording(1, order));
    for (int i = 2; i < count; i++) {
      callbacks = callbacks.add(recording(i, order));
    }
    return callbacks;
  }

  private static BiConsumer<String, Throwable> recording(
      final int index, final List<Integer> order) {
    return (value, error) -> order.add(index);
  }

  private static List<Integer> range(final int count) {
    final var range = new ArrayList<Integer>();
    for (int i = 0; i < count; i++) {
      range.add(i);
    }
    return range;
  }
}
