/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.transport;

import org.agrona.DirectBuffer;

@FunctionalInterface
public interface RequestHandler {

  /**
   * Called on new request on given partition.
   *
   * @param serverOutput output to write the response
   * @param partitionId the corresponding partition id
   * @param requestId the id of the request which should be handled
   * @param buffer the buffer which contains the request
   * @param offset the offset in the request buffer
   * @param length the length of the request buffer
   */
  void onRequest(
      ServerOutput serverOutput,
      int partitionId,
      long requestId,
      DirectBuffer buffer,
      int offset,
      int length);
}
