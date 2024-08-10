/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/** Utility class for common tasks with {@link AtomicReference} instances. */
public final class AtomicUtil {

  private AtomicUtil() {}

  /**
   * Locklessly replaces the value of an atomic reference using the provided replacer function and
   * rollbacks if the value was replaced concurrently.
   *
   * <p>If the replacer function returns an empty optional, the value of the atomic reference is not
   * replaced.
   *
   * <p>If the value of the atomic reference has been replaced by another thread in the meantime,
   * the replacer function is called again. Additionally, the rollback function is applied to any
   * non-empty value returned by the previous replacer function call.
   *
   * @param ref The atomic reference that holds the value to replace
   * @param replacer The replacer function used to provide a new value to the atomic reference; if
   *     {@code empty} the value of atomic reference is not replaced
   * @param rollback The rollback function used to revert any side effect produced by the replacer
   *     function
   * @return The previous value of the atomic reference, or null if the value was not replaced
   * @param <T> The type of the value of the atomic reference
   */
  public static <T> T replace(
      final AtomicReference<T> ref,
      final Function<T, Optional<T>> replacer,
      final Consumer<T> rollback) {
    T currentVal = null;
    T newVal = null;
    do {
      if (newVal != null && newVal != currentVal) {
        // another thread appears to have replaced the value in the meantime
        rollback.accept(newVal);
      }
      currentVal = ref.get();
      final var result = replacer.apply(currentVal);
      newVal = result.orElse(currentVal);
    } while (!ref.compareAndSet(currentVal, newVal));
    return currentVal == newVal ? null : currentVal;
  }
}
