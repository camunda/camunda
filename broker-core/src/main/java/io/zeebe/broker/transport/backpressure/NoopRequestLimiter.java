/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.backpressure;

public class NoopRequestLimiter<ContextT> implements RequestLimiter<ContextT> {

  @Override
  public boolean tryAcquire(int streamId, long requestId, ContextT context) {
    return true;
  }

  @Override
  public void onResponse(int streamId, long requestId) {}

  @Override
  public void onIgnore(int streamId, long requestId) {}

  @Override
  public int getLimit() {
    return 0;
  }

  @Override
  public int getInflightCount() {
    return 0;
  }
}
