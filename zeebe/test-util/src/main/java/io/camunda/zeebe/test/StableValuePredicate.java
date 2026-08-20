/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.awaitility.Awaitility;

public final class StableValuePredicate<T> implements Predicate<T> {

  final AtomicReference<T> lastSeen = new AtomicReference<>();

  /**
   * Used in combination with {@link Awaitility}'s {@link
   * org.awaitility.core.ConditionFactory#during(Duration)} to ensure that an expression maintains
   * an arbitrary value over time.
   *
   * @return a predicate that accepts a value if it is the same value that was checked in the
   *     previous call to this predicate.
   */
  public static <T> StableValuePredicate<T> hasStableValue() {
    return new StableValuePredicate<>();
  }

  @Override
  public boolean test(final T t) {
    return Objects.equals(t, lastSeen.getAndSet(t));
  }
}
