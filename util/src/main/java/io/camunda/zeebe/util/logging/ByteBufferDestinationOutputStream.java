/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.logging;

import java.io.IOException;
import java.io.OutputStream;
import org.apache.logging.log4j.core.layout.ByteBufferDestination;

public final class ByteBufferDestinationOutputStream extends OutputStream {
  private final ByteBufferDestination destination;

  public ByteBufferDestinationOutputStream(final ByteBufferDestination destination) {
    this.destination = destination;
  }

  @Override
  public void write(final int b) throws IOException {
    final var bytes = new byte[] {(byte) b};
    write(bytes);
  }

  @Override
  public void write(final byte[] bytes) throws IOException {
    write(bytes, 0, bytes.length);
  }

  @Override
  public void write(final byte[] bytes, final int off, final int len) throws IOException {
    destination.writeBytes(bytes, off, len);
  }
}
