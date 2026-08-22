/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el;

import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The value an {@link EvaluationContext} resolves a name to.
 *
 * <p>A context backed by storage produces {@link MsgPack}. A context handing back the result of an
 * earlier evaluation produces {@link Evaluated} instead, so that result's FEEL type survives into
 * the next evaluation: MessagePack has no representation for a duration, date or time, so a round
 * trip through it silently turns them into strings.
 *
 * <p>{@link Structure} is for a context that assembles an object itself rather than reading one
 * from storage. Its entries are a snapshot — whoever builds it must not mutate the map afterwards.
 */
@NullMarked
public sealed interface ContextValue {

  /**
   * Wraps a MessagePack buffer, mapping a {@code null} or empty buffer to {@code null} — the value
   * {@link EvaluationContext#getVariable(String)} uses to signal that a name is absent.
   */
  static @Nullable ContextValue msgPack(final @Nullable DirectBuffer buffer) {
    return buffer != null && buffer.capacity() > 0 ? new MsgPack(buffer) : null;
  }

  /** A value already encoded as MessagePack, typically read from storage. */
  record MsgPack(DirectBuffer buffer) implements ContextValue {}

  /** An earlier evaluation's result, not yet serialized, so its FEEL type survives. */
  record Evaluated(EvaluationResult result) implements ContextValue {}

  /** An object assembled by the caller; its entries are themselves context values. */
  record Structure(Map<String, ContextValue> entries) implements ContextValue {}
}
