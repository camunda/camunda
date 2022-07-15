/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.agrona.IoUtil;

final class TemplateSegmentAllocator implements SegmentAllocator {
  private final Path templateFile;
  private final SegmentAllocator baseAllocator;

  TemplateSegmentAllocator(final Path templateFile) {
    this(templateFile, new DefaultSegmentAllocator());
  }

  TemplateSegmentAllocator(final Path templateFile, final SegmentAllocator baseAllocator) {
    this.templateFile = templateFile;
    this.baseAllocator = baseAllocator;
  }

  @Override
  public FileChannel allocate(
      final Path segmentPath,
      final JournalSegmentDescriptor descriptor,
      final long lastWrittenIndex)
      throws IOException {
    final FileChannel segmentChannel;
    try (final var source = FileChannel.open(templateFile, StandardOpenOption.READ)) {
      segmentChannel = baseAllocator.allocate(segmentPath, descriptor, lastWrittenIndex);

      try {
        source.transferTo(0, descriptor.maxSegmentSize(), segmentChannel);
      } catch (final IOException e) {
        segmentChannel.close();
        throw e;
      }
    }

    return segmentChannel;
  }

  static TemplateSegmentAllocator of(final Path path, final long size) throws IOException {
    try (final var channel =
        FileChannel.open(
            path,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.DELETE_ON_CLOSE)) {
      IoUtil.fill(channel, 0, size, (byte) 0);
    }

    return new TemplateSegmentAllocator(path);
  }
}
