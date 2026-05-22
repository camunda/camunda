/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.api;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.agrona.DirectBuffer;

/**
 * Represents a consumer of a stream which can consume data pushed from the server. The data is
 * typically pushed from a broker, and consumed by gateway.
 */
@FunctionalInterface
public interface ClientStreamConsumer {

  /**
   * Consumes the payload received from the server to the client. It is recommended to make the
   * implementation to be asynchronous. Otherwise, it could block the thread of {@link
   * ClientStreamService} and thus possibly delaying data from other streams being pushed.
   *
   * @param payload the data to be consumed by the client
   */
  ActorFuture<Void> push(DirectBuffer payload);
}
