/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.allocation;

public class BufferAllocators {
  private static final DirectBufferAllocator DIRECT_BUFFER_ALLOCATOR = new DirectBufferAllocator();

  public static AllocatedBuffer allocateDirect(int capacity) {
    return DIRECT_BUFFER_ALLOCATOR.allocate(capacity);
  }
}
