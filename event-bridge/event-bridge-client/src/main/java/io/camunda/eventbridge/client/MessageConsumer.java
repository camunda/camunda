/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

/**
 * Handle to a running managed consumer started via {@link ConsumerBuilder#start()}. A background
 * daemon thread polls, dispatches each record to the registered {@link MessageHandler}, and (with
 * auto-commit on) commits the max processed offset per partition after each batch.
 *
 * <p>{@link #close()} stops the loop, performs a final commit (when auto-commit is on), and closes
 * the underlying {@link Consumer}. Idempotent.
 */
public interface MessageConsumer extends AutoCloseable {

  /** Returns the underlying group-membership {@link Consumer} this managed loop drives. */
  Consumer consumer();

  @Override
  void close();
}
