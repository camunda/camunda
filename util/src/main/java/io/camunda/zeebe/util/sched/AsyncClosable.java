/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.future.ActorFuture;

public interface AsyncClosable {

  /**
   * Asynchronous closing. The implementation should close related resources and return a future,
   * which is complete when closing is done.
   *
   * @return the future, which is completed when resources are closed
   */
  ActorFuture<Void> closeAsync();
}
