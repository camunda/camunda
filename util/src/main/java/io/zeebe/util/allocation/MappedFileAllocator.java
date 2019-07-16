/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.allocation;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;

/** Allocates a buffer in a mapped file. */
public class MappedFileAllocator implements BufferAllocator {

  private final File mappedFile;

  public MappedFileAllocator(File mappedFile) {
    super();
    this.mappedFile = mappedFile;
  }

  @Override
  public AllocatedBuffer allocate(int capacity) {
    RandomAccessFile raf = null;

    try {
      raf = new RandomAccessFile(mappedFile, "rw");

      final MappedByteBuffer mappedBuffer = raf.getChannel().map(MapMode.READ_WRITE, 0, capacity);

      return new AllocatedMappedFile(mappedBuffer, raf);
    } catch (Exception e) {
      if (raf != null) {
        try {
          raf.close();
        } catch (IOException e1) {
          // ignore silently
        }
      }

      throw new RuntimeException(
          "Could not map file " + mappedFile + " into memory: " + e.getMessage(), e);
    }
  }
}
