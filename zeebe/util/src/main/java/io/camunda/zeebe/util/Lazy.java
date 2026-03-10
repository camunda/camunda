/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A lazily-evaluated value that defers computation until first access and caches the result.
 *
 * <p>Thread safety: under contention the supplier may be invoked more than once, but all threads
 * will converge on the same result (the CAS winner's value). The supplier is not retained after
 * evaluation.
 *
 * @param <T> the type of the lazily-computed value (may be null)
 */
public final class Lazy<T> implements Supplier<T> {

  // guard to avoid looping if user provided supplier returns null
  private static final Object UNINITIALIZED = new Object();

  private final AtomicReference<Object> value = new AtomicReference<>(UNINITIALIZED);
  private volatile Supplier<? extends T> supplier;

  private Lazy(final Supplier<? extends T> supplier) {
    this.supplier = Objects.requireNonNull(supplier, "supplier must not be null");
  }

  /** Creates a new lazy value that will be computed by the given supplier on first access. */
  public static <T> Lazy<T> of(final Supplier<? extends T> supplier) {
    return new Lazy<>(supplier);
  }

  @Override
  @SuppressWarnings("unchecked")
  public T get() {
    final var current = value.get();
    if (current != UNINITIALIZED) {
      return (T) current;
    }

    final var computed = supplier.get();
    if (value.compareAndSet(UNINITIALIZED, computed)) {
      supplier = null; // release reference after successful evaluation
    }
    return (T) value.get();
  }

  /** Returns {@code true} if the value has been computed, without forcing evaluation. */
  public boolean isEvaluated() {
    return value.get() != UNINITIALIZED;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(get());
  }

  @Override
  public boolean equals(final Object o) {
    return this == o || (o instanceof final Lazy<?> other && Objects.equals(get(), other.get()));
  }

  @Override
  public String toString() {
    return String.valueOf(get());
  }
}
