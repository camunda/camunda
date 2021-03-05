/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.impl.backpressure;

public final class NoopAppendLimiter implements AppendLimiter {

  @Override
  public boolean tryAcquire(final Long position) {
    return true;
  }

  @Override
  public void onCommit(final long position) {}

  @Override
  public int getInflight() {
    return 0;
  }

  @Override
  public int getLimit() {
    return 0;
  }
}
