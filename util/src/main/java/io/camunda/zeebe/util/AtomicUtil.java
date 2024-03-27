/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
   * Locklessly updates an atomic reference using the provided update function and rollbacks if the
   * reference changed during the update, e.g. by another thread.
   *
   * <p>If the update function returns an empty optional, the atomic reference is not updated.
   *
   * <p>If the atomic reference has been updated by another thread in the meantime, the rollback
   * function is called with the value provided by the update function, and the update function is
   * called again.
   *
   * @param ref The atomic reference to update
   * @param update The update function used to provide a new value to the atomic reference; if
   *     {@code empty} the atomic reference is not updated
   * @param rollback The rollback function used to revert any side effect produced by the update
   *     function
   * @return The previous value of the atomic reference, or null if the value was left unchanged
   * @param <T> The type of the atomic reference
   */
  public static <T> T update(
      final AtomicReference<T> ref,
      final Function<T, Optional<T>> update,
      final Consumer<T> rollback) {
    T currentVal;
    T newVal = null;
    do {
      if (newVal != null) {
        // another thread appears to have replaced the value in the meantime
        rollback.accept(newVal);
      }
      currentVal = ref.get();
      final var result = update.apply(currentVal);
      if (result.isEmpty()) {
        return null;
      }
      newVal = result.get();
    } while (!ref.compareAndSet(currentVal, newVal));
    return currentVal;
  }
}
