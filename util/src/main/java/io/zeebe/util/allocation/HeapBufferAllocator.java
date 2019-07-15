/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.allocation;

import java.nio.ByteBuffer;

public class HeapBufferAllocator implements BufferAllocator {

  @Override
  public AllocatedBuffer allocate(int capacity) {
    return new ExternallyAllocatedBuffer(ByteBuffer.allocate(capacity));
  }
}
