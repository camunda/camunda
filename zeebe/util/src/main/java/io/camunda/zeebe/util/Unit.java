/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

public final class Unit {
  private Unit() {}

  /**
   * {@link java.lang.Void} type has no value, but at runtime it's always represented by null. Per
   * jspecify, null cannot be assigned `Void`, but it can assigned to `@Nullable Void`.
   *
   * <p>To avoid polluting every Future with `@Nullable Void` you can use this helper that already
   * suppress NullAway warnings. For example:
   *
   * <pre><code>
   *    var future = new CompletableFuture<Void>();
   *    future.complete(unit());
   * </code></pre>
   *
   * @return an instance of Void
   */
  @SuppressWarnings("NullAway")
  public static Void unit() {
    return null;
  }
}
