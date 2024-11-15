/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SubClassOfTest {
  @Test
  public void shouldReturnCorrectSubclass() {
    final var subclass =
        SubClassOf.make(IllegalArgumentException.class, IllegalStateException.class);

    assertTrue(subclass.test(new IllegalArgumentException("")));
    assertTrue(subclass.test(new IllegalStateException("")));
    assertTrue(subclass.test(new MyIllegalArgumentException()));
    assertFalse(subclass.test(new RuntimeException("")));
  }

  @Test
  public void shouldNotReturnTrueIfEmpty() {
    final var subclass = SubClassOf.make();
    final var objects = new Object[] {"", 1, 1.0, new Object[] {}};

    for (final var obj : objects) {
      assertFalse(subclass.test(obj));
    }
  }

  public static final class MyIllegalArgumentException extends IllegalArgumentException {}
}
