/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.log;

import io.zeebe.util.sched.future.ActorFuture;
import java.util.Optional;

public interface LogStreamWriter {

  /**
   * Attempts to write the event to the underlying stream. If the writer failed to write to the
   * Dispatcher, the Optional will be empty. Otherwise, it will contain a future that will be
   * completed when the event's position is generated.
   *
   * @return an optional with a future to be completed with the position after it's written
   */
  Optional<ActorFuture<Long>> tryWrite();
}
