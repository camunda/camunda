/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.allocation;

import io.zeebe.util.Loggers;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import org.slf4j.Logger;

public class AllocatedMappedFile extends AllocatedBuffer {
  private static final Logger LOG = Loggers.IO_LOGGER;

  protected final RandomAccessFile raf;

  public AllocatedMappedFile(ByteBuffer buffer, RandomAccessFile raf) {
    super(buffer);
    this.raf = raf;
  }

  @Override
  public void doClose() {
    try {
      raf.close();
    } catch (IOException e) {
      LOG.warn("Failed to close mapped file.", e);
    }
  }

  public RandomAccessFile getFile() {
    return raf;
  }
}
