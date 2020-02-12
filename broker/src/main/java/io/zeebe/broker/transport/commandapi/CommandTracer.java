/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public interface CommandTracer {
  Span start(DirectBuffer parentContext, int partitionId, long requestId);

  void finish(int partitionId, long requestId, final Consumer<Span> spanConsumer);

  final class NoopCommandTracer implements CommandTracer {

    @Override
    public Span start(DirectBuffer parentContext, int partitionId, long requestId) {
      return NoopSpan.INSTANCE;
    }

    @Override
    public void finish(int partitionId, long requestId, final Consumer<Span> spanConsumer) {
      // noop
    }
  }
}
