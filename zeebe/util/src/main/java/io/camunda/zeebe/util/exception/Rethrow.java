/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.exception;

public final class Rethrow {

  private Rethrow() {}

  /**
   * Rethrow an {@link java.lang.Throwable} preserving the stack trace but making it unchecked.
   *
   * @param ex to be rethrown and unchecked.
   */
  public static <A> A rethrowUnchecked(final Throwable ex) {
    return Rethrow.<A, RuntimeException>rethrow(ex);
  }

  @SuppressWarnings("unchecked")
  private static <A, T extends Throwable> A rethrow(final Throwable t) throws T {
    throw (T) t;
  }
}
