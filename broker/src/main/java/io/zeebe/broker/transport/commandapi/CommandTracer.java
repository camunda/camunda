/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import org.agrona.DirectBuffer;

public interface CommandTracer {
  void start(DirectBuffer parentContext, int partitionId, long requestId);

  void finish(int partitionId, long requestId, boolean failed);

  final class NoopCommandTracer implements CommandTracer {

    @Override
    public void start(DirectBuffer parentContext, int partitionId, long requestId) {
      // noop
    }

    @Override
    public void finish(int partitionId, long requestId, boolean failed) {
      // noop
    }
  }
}
