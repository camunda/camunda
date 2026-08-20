/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.util.function.Predicate;

/**
 * Predicate to test if a object is a subclass of some types. Usage:
 *
 * <pre>{@code
 * final var subClassOf = SubClassOf.make(
 *   IllegalArgumentException.class,
 *   IllegalStateException.class
 *   );
 * subclassOf.test(new Exception("Error"));
 *
 * }</pre>
 *
 * @param classes
 * @param <A>
 */
public record SubClassOf<A>(Class<? extends A>[] classes) implements Predicate<A> {

  @Override
  public boolean test(final A a) {
    for (final var clzz : classes) {
      if (clzz.isAssignableFrom(a.getClass())) {
        return true;
      }
    }
    return false;
  }

  @SafeVarargs
  public static <A> SubClassOf<A> make(final Class<? extends A>... classes) {
    return new SubClassOf<>(classes);
  }
}
