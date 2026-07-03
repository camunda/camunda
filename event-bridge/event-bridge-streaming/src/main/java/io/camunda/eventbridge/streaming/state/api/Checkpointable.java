/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state.api;

/**
 * A store that buffers writes and must be told when to make them durable. The runtime calls {@link
 * #checkpoint()} inside its commit transaction, so the buffered changes land in the same atomic cut
 * as the consumed offsets — a crash then replays from the source rather than losing them.
 */
public interface Checkpointable {

  /** Flushes buffered writes to the durable delegate. Called within the commit transaction. */
  void checkpoint();
}
