/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.JournalException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultSegmentAllocator implements SegmentAllocator {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSegmentAllocator.class);

  @Override
  public FileChannel allocate(
      final Path segmentPath,
      final JournalSegmentDescriptor descriptor,
      final long lastWrittenIndex)
      throws IOException {
    if (Files.exists(segmentPath)) {
      tryReuseExistingSegmentFile(segmentPath, descriptor.index(), lastWrittenIndex);
    }

    return FileChannel.open(
        segmentPath, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
  }

  private void tryReuseExistingSegmentFile(
      final Path segmentPath, final long segmentIndex, final long lastWrittenIndex)
      throws IOException {
    // do not reuse a segment into which we've already written!
    if (lastWrittenIndex >= segmentIndex) {
      throw new JournalException(
          String.format(
              "Failed to create journal segment %s, as it already exists, and the last written "
                  + "index %d indicates we've already written to it",
              segmentPath, lastWrittenIndex));
    }

    LOGGER.warn(
        "Failed to create segment {}: an unused file already existed, and will be replaced",
        segmentPath);
    Files.delete(segmentPath);
  }
}
