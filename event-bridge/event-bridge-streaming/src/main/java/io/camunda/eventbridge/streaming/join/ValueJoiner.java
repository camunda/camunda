/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.join;

/**
 * Combines a stream record with the table value looked up for its join key. Runs on the runtime
 * thread inside {@link StreamTableJoin#process}, so it must read everything it needs from {@code
 * right} synchronously: the value is a store flyweight, valid only until the next store call — copy
 * out any fields kept in the result.
 *
 * @param <Left> the stream record type
 * @param <Right> the table value type (a store value flyweight)
 * @param <Out> the joined result type
 */
@FunctionalInterface
public interface ValueJoiner<Left, Right, Out> {

  /**
   * Produces the joined value. On a left-outer join with no matching table entry, {@code right} is
   * {@code null}. Returning {@code null} drops the record (nothing is forwarded downstream).
   *
   * @param left the stream record
   * @param right the matched table value flyweight, or {@code null} on a left-outer miss
   */
  Out join(Left left, Right right);
}
