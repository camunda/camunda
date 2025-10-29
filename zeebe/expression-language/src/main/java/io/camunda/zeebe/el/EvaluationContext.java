/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el;

import io.camunda.zeebe.util.Either;
import org.agrona.DirectBuffer;

/**
 * A read-only view over a variable context, typically backed by a large JSON-like buffer.
 *
 * <p>Variable resolution proceeds one segment at a time. Calling {@link #getVariable(String)}
 * returns either:
 *
 * <ul>
 *   <li><b>Left(DirectBuffer)</b> is a terminal value (e.g., a primitive, string) associated with
 *       the provided variable name in this context, or {@code Left(null)} to signal that no value
 *       exists for the provided name in this context
 *   <li><b>Right(EvaluationContext)</b> is a nested context representing an object/node which can
 *       be queried further for deeper segments.
 * </ul>
 *
 * <h3>Resolution semantics</h3>
 *
 * <pre>{@code
 * // Example path resolution for "camunda.vars.cluster.key":
 * Either<DirectBuffer, EvaluationContext> step1 = root.getVariable("camunda");
 * // if Right(ctx1), then:
 * Either<DirectBuffer, EvaluationContext> step2 = ctx1.getVariable("vars");
 * // if Right(ctx2), then:
 * Either<DirectBuffer, EvaluationContext> step3 = ctx2.getVariable("cluster");
 * // if Right(ctx3), then:
 * Either<DirectBuffer, EvaluationContext> step4 = ctx3.getVariable("key");
 * // step4 is expected to be Left(value) or Left(null) if not found
 * }</pre>
 *
 * <h3>Null and absence</h3>
 *
 * Returning {@code Left(null)} indicates <em>absence</em> (variable not present).
 *
 * <h3>Buffer lifetime</h3>
 *
 * Unless otherwise documented by a given implementation, the {@link DirectBuffer} returned in
 * {@code Left} is considered valid only until the next call into the same implementation.
 */
@FunctionalInterface
public interface EvaluationContext {

  /**
   * Looks up a variable in the current context by its immediate name (one path segment).
   *
   * @param variableName the single-segment variable name to resolve in this context
   * @return an {@link Either} containing:
   *     <ul>
   *       <li><b>Left(valueBuffer)</b> when the name maps to a terminal value; or {@code
   *           Left(null)} when the name is absent in this context; or
   *       <li><b>Right(nestedContext)</b> when the name maps to an object/structure that can be
   *           queried further for deeper segments.
   *     </ul>
   *     Implementations should never return {@code Right(null)}.
   */
  Either<DirectBuffer, EvaluationContext> getVariable(String variableName);
}
