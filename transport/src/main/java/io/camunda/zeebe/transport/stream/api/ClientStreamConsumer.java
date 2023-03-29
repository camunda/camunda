/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.api;

import org.agrona.DirectBuffer;

/**
 * Represents a consumer of a stream which can consume data of type P. The data is typically pushed
 * from a broker, and consumed by gateway.
 *
 * @param <P> the payload type that can be pushed to the stream
 */
public interface ClientStreamConsumer {

  /**
   * Consumes the payload received from the server to the client. Implementation of this method can
   * be asynchronous.
   *
   * @param payload the data to be consumed by the client
   */
  void push(DirectBuffer payload);
}
