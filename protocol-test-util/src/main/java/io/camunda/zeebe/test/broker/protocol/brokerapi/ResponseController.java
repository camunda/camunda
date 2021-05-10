/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol.brokerapi;

import java.util.concurrent.CyclicBarrier;

public final class ResponseController {

  protected final CyclicBarrier barrier =
      new CyclicBarrier(2); // two parties: broker thread responding and test thread signalling

  /**
   * Unblocks the sender for sending this response. If the sender is not yet blocked, the calling
   * thread is blocked until then.
   */
  public void unblockNextResponse() {
    waitForNextJoin();
  }

  protected void waitForNextJoin() {
    try {
      barrier.await();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
