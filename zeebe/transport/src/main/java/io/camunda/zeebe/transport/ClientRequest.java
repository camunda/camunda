/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.util.buffer.BufferWriter;

public interface ClientRequest extends BufferWriter {

  /**
   * @return the partition id to which the request should be sent to
   */
  int getPartitionId();

  /**
   * @return the type of this request
   */
  RequestType getRequestType();

  /**
   * @return the partition group (physical tenant) this request targets; defaults to {@code
   *     "default"} for backward compatibility
   */
  default String getPartitionGroup() {
    return Protocol.DEFAULT_PARTITION_GROUP_NAME;
  }
}
