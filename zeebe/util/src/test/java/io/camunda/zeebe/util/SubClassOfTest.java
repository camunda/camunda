/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;

class SubClassOfTest {
  @Test
  public void shouldReturnCorrectSubclass() {
    final var subclass =
        SubClassOf.make(IllegalArgumentException.class, IllegalStateException.class);

    assertThat(subclass.test(new IllegalArgumentException(""))).isTrue();
    assertThat(subclass.test(new IllegalStateException(""))).isTrue();
    assertThat(subclass.test(new MyIllegalArgumentException())).isTrue();
    assertThat(subclass.test(new RuntimeException(""))).isFalse();
  }

  @Test
  public void shouldNotReturnTrueIfEmpty() {
    final var subclass = SubClassOf.make();
    final var objects = new Object[] {"", 1, 1.0, new Object[] {}};

    for (final var obj : objects) {
      assertThat(subclass.test(obj)).isFalse();
    }
  }

  private static final class MyIllegalArgumentException extends IllegalArgumentException {}
}
