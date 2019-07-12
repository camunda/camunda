/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import io.zeebe.util.buffer.BufferWriter;

public interface ServerOutput {
  /**
   * Sends a message according to the single message protocol.
   *
   * <p>Returns false if the message cannot be currently written due to exhausted capacity. Throws
   * an exception if the request is not sendable at all (e.g. buffer writer throws exception).
   */
  boolean sendMessage(int streamId, BufferWriter writer);

  /**
   * Sends a response according to the request response protocol.
   *
   * <p>Returns null if the response cannot be currently written due to exhausted capacity. Throws
   * an exception if the response is not sendable at all (e.g. buffer writer throws exception).
   */
  boolean sendResponse(ServerResponse response);
}
