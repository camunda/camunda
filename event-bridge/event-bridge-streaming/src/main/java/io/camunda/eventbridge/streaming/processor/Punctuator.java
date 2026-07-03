/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.processor;

/**
 * A periodic callback registered with {@link ProcessorContext#schedule}. It runs on the runtime
 * thread, in between records, so it may safely touch the same state and {@link
 * ProcessorContext#forward forward} downstream as {@link Processor#process}.
 */
@FunctionalInterface
public interface Punctuator {

  /**
   * Invoked when the scheduled interval elapses.
   *
   * @param timestampMs the current wall-clock or stream-time (per the {@link PunctuationType}), in
   *     epoch milliseconds
   */
  void punctuate(long timestampMs);
}
