/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

/**
 * Interface for types that support zero-serialization copying. Implementations copy their internal
 * state into a target of the same concrete type without going through any serialization format.
 *
 * @param <T> the concrete type that can be copied
 */
public interface Copyable<T> {

  /**
   * Copies the internal state of this object into {@code target}. Both must be the same concrete
   * type. No allocation, no serialization — just field-level assignment.
   */
  void copyTo(T target);

  /**
   * Creates a new, empty instance of the same concrete type. Used to allocate storage slots without
   * knowing the concrete type at compile time.
   */
  T newInstance();
}
