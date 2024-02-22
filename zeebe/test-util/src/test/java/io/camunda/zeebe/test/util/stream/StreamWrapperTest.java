/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

public final class StreamWrapperTest {

  @Test
  public void shouldSkipElementsBasedOnPredicate() {
    // given
    final Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
    final IntegerStream wrapper = new IntegerStream(stream);

    // when
    final List<Integer> result = wrapper.skipUntil(i -> i == 3).asList();

    // then
    assertThat(result).containsExactly(3, 4, 5);
  }

  class IntegerStream extends StreamWrapper<Integer, IntegerStream> {

    IntegerStream(final Stream<Integer> wrappedStream) {
      super(wrappedStream);
    }

    @Override
    protected IntegerStream supply(final Stream<Integer> wrappedStream) {
      return new IntegerStream(wrappedStream);
    }
  }
}
