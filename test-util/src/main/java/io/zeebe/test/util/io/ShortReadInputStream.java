/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Only reads the first {@code bytesToRead} bytes from the underlying input stream. After that it
 * returns on every read {@link ShortReadInputStream#END_OF_STREAM} if {@code throwException} is
 * false, or an {@link IOException} otherwise.
 */
public final class ShortReadInputStream extends InputStream {

  public static final int END_OF_STREAM = -1;

  private final InputStream underlyingInputStream;
  private final long bytesToRead;
  private final boolean throwException;

  private long readCount;

  public ShortReadInputStream(
      final InputStream underlyingInputStream,
      final long bytesToRead,
      final boolean throwException) {
    this.underlyingInputStream = underlyingInputStream;
    this.bytesToRead = bytesToRead;
    this.throwException = throwException;

    readCount = 0;
  }

  @Override
  public int read() throws IOException {
    readCount++;

    if (readCount > bytesToRead) {
      if (throwException) {
        throw new IOException("Read failure");
      } else {
        return END_OF_STREAM;
      }
    } else {
      return underlyingInputStream.read();
    }
  }
}
