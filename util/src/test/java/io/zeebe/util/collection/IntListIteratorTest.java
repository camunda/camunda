/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class IntListIteratorTest {

  private static final List<Integer> NUMBERS = new ArrayList<>();

  static {
    NUMBERS.add(1);
    NUMBERS.add(2);
    NUMBERS.add(3);
  }

  @Test
  public void shouldIterateArrayListBoxed() {
    // when
    final IntListIterator iterator = new IntListIterator(NUMBERS);

    // then
    assertThat(iterator).toIterable().containsExactly(1, 2, 3);
  }

  @Test
  public void shouldIterateArrayListPrimitive() {
    // when
    final IntListIterator iterator = new IntListIterator(NUMBERS);

    // then
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.nextInt()).isEqualTo(1);
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.nextInt()).isEqualTo(2);
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.nextInt()).isEqualTo(3);
    assertThat(iterator.hasNext()).isFalse();
  }
}
