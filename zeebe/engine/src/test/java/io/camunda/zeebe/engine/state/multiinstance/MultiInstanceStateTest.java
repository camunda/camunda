/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableMultiInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class MultiInstanceStateTest {
  private MutableProcessingState processingState;
  private MutableMultiInstanceState multiInstanceState;

  @BeforeEach
  public void setup() {
    multiInstanceState = processingState.getMultiInstanceState();
  }

  @Test
  void shouldUpsertAndGetInputCollection() {
    // given
    final long key = 123L;
    final List<DirectBuffer> inputCollection =
        List.of(new UnsafeBuffer("foo".getBytes()), new UnsafeBuffer("bar".getBytes()));

    // when
    multiInstanceState.upsertInputCollection(key, inputCollection);

    // then
    final Optional<List<DirectBuffer>> result = multiInstanceState.getInputCollection(key);
    assertThat(result).isPresent();
    assertThat(result.get()).hasSize(2);
    assertThat(result.get().get(0)).isEqualTo(inputCollection.get(0));
    assertThat(result.get().get(1)).isEqualTo(inputCollection.get(1));
  }

  @Test
  void shouldDeleteInputCollection() {
    // given
    final long key = 456L;
    final List<DirectBuffer> input = List.of(new UnsafeBuffer("baz".getBytes()));
    multiInstanceState.upsertInputCollection(key, input);

    // when
    multiInstanceState.deleteInputCollection(key);

    // then
    final Optional<List<DirectBuffer>> result = multiInstanceState.getInputCollection(key);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldUpdateExistingInputCollectionOnDuplicateUpsert() {
    // given
    final long key = 321L;
    final List<DirectBuffer> initialCollection =
        List.of(new UnsafeBuffer("foo".getBytes()), new UnsafeBuffer("bar".getBytes()));
    multiInstanceState.upsertInputCollection(key, initialCollection);

    // when - calling upsert again with the same key (e.g. on incident resolution re-activation)
    final List<DirectBuffer> updatedCollection = List.of(new UnsafeBuffer("baz".getBytes()));
    multiInstanceState.upsertInputCollection(key, updatedCollection);

    // then - the value is overwritten without throwing an exception
    final Optional<List<DirectBuffer>> result = multiInstanceState.getInputCollection(key);
    assertThat(result).isPresent();
    assertThat(result.get()).hasSize(1);
    assertThat(result.get().getFirst()).isEqualTo(updatedCollection.getFirst());
  }

  @Test
  void shouldReturnEmptyForMissingInputCollection() {
    // given
    final long key = 789L;

    // when
    final Optional<List<DirectBuffer>> result = multiInstanceState.getInputCollection(key);

    // then
    assertThat(result).isEmpty();
  }
}
