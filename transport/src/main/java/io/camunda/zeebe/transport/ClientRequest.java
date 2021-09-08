/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport;

import io.camunda.zeebe.util.buffer.BufferWriter;

public interface ClientRequest extends BufferWriter {

  /** @return the partition id to which the request should be send to */
  int getPartitionId();

  /** @return the type of this request */
  RequestType getRequestType();
}
