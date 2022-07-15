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

interface SegmentAllocator {
  FileChannel allocate(
      final Path segmentPath,
      final JournalSegmentDescriptor descriptor,
      final long lastWrittenIndex)
      throws IOException;
}
