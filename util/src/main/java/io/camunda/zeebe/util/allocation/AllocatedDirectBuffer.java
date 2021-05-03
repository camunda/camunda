/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.allocation;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public final class AllocatedDirectBuffer extends AllocatedBuffer {
  private final Consumer<AllocatedDirectBuffer> onCloseCallback;

  AllocatedDirectBuffer(final ByteBuffer buffer, final Consumer<AllocatedDirectBuffer> onClose) {
    super(buffer);
    onCloseCallback = onClose;
  }

  @Override
  public void doClose() {
    onCloseCallback.accept(this);
  }
}
