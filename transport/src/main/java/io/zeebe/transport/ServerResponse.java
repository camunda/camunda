/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.transport;

import io.zeebe.util.buffer.BufferWriter;

public interface ServerResponse extends BufferWriter {

  /** @return the id of the corresponding request */
  long getRequestId();

  /** @return the partition id on which the requests was received and should be send back */
  int getPartitionId();
}
