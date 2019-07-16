/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl.memory;

import io.zeebe.transport.Loggers;
import java.nio.ByteBuffer;
import org.slf4j.Logger;

/**
 * used for transports where you do not need to limit memory (like client requests in the zeebe
 * broker)
 */
public class UnboundedMemoryPool implements TransportMemoryPool {
  private static final Logger LOG = Loggers.TRANSPORT_MEMORY_LOGGER;

  @Override
  public ByteBuffer allocate(int requestedCapacity) {
    LOG.trace("Attocated {} bytes", requestedCapacity);
    return ByteBuffer.allocate(requestedCapacity);
  }

  @Override
  public void reclaim(ByteBuffer buffer) {
    final int bytesReclaimed = buffer.capacity();
    LOG.trace("Reclaiming {} bytes", bytesReclaimed);
  }
}
