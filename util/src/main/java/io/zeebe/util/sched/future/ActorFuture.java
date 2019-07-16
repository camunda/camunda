/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.future;

import io.zeebe.util.sched.ActorTask;
import java.util.concurrent.Future;

/** interface for actor futures */
public interface ActorFuture<V> extends Future<V> {
  void complete(V value);

  void completeExceptionally(String failure, Throwable throwable);

  void completeExceptionally(Throwable throwable);

  V join();

  /** To be used by scheduler only */
  void block(ActorTask onCompletion);

  boolean isCompletedExceptionally();

  Throwable getException();
}
