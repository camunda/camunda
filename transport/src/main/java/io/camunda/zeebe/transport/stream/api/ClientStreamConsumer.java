/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.api;

import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;

/**
 * Represents a consumer of a stream which can consume data pushed from the server. The data is
 * typically pushed from a broker, and consumed by gateway.
 */
public interface ClientStreamConsumer {

  /**
   * Consumes the payload received from the server to the client. It is recommended to make the
   * implementation to be asynchronous. Otherwise, it could block the thread of {@link
   * ClientStreamService} and thus possibly delaying data from other streams being pushed.
   *
   * @param payload the data to be consumed by the client
   */
  CompletableFuture<Void> push(DirectBuffer payload);
}
