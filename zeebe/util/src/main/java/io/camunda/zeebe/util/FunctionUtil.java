/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class FunctionUtil {
  private FunctionUtil() {}

  /**
   * A utility method to easily peak at a value and return itself. Mostly used for cases where you
   * want to chain and the API expects a {@link java.util.function.Function}, but you just want to
   * accept via a {@link Consumer} and return the original value as is, i.e. you want to take a peek
   * at the value.
   *
   * <p>For example, can be useful to peek at a value during a future chain:
   *
   * <pre>{@code
   * final CompletableFuture<Object> myFuture = api.getAsync();
   * return myFuture.thenApply(FunctionUtil.peek(value -> log(value)));
   * }</pre>
   *
   * This is especially useful, because alternatives would be using `thenAccept`, which would erase
   * the value from the return chain, or starting a different pipeline by not chaining directly. If
   * the order of operations does not matter to you, then you can gladly use the multiple pipelines.
   * If the order matters (and in most cases, it does, if only to be able to still clearly think
   * about how the code is executed), then you should use this.
   *
   * @param consumer the consumer which will peek at the value; ideally should be side-effect free
   * @return a {@link UnaryOperator} which will simply return the same value as it was given
   * @param <T> the type of the expected value
   */
  public static <T> UnaryOperator<T> peek(final Consumer<T> consumer) {
    return value -> {
      consumer.accept(value);
      return value;
    };
  }
}
