/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import org.agrona.DirectBuffer;

/**
 * Response obtained to a client request. See {@link ClientOutput#sendRequest(RemoteAddress,
 * io.zeebe.util.buffer.BufferWriter)} and others.
 */
public interface ClientResponse {
  /** @return the remote address from which the response was obtained */
  RemoteAddress getRemoteAddress();

  /** @return the id of the request */
  long getRequestId();

  /** @return the response data */
  DirectBuffer getResponseBuffer();
}
